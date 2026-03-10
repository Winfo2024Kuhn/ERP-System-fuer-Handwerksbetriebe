"""
Codebase Indexer for KI-Hilfe RAG System
=========================================
Scans Java + React source code, splits into semantic chunks,
embeds via Gemini text-embedding-004, and stores in Qdrant.

Usage:
    python indexer.py                        # Full reindex
    python indexer.py --collection codebase  # Custom collection name
    python indexer.py --dry-run              # Preview chunks without uploading
"""

import argparse
import hashlib
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

import google.generativeai as genai
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

# ── Configuration ──────────────────────────────────────────────

GEMINI_EMBEDDING_MODEL = "models/text-embedding-004"
EMBEDDING_DIMENSION = 768  # text-embedding-004 output dimension
QDRANT_HOST = "localhost"
QDRANT_PORT = 6333
DEFAULT_COLLECTION = "codebase"
BATCH_SIZE = 20  # Embeddings per API call (Gemini limit ~100)
MAX_CHUNK_CHARS = 6000  # Roughly ~1500 tokens
MIN_CHUNK_CHARS = 50

# File scanning config
PROJECT_ROOT = Path(__file__).resolve().parent.parent
JAVA_BASE = "src/main/java/org/example/kalkulationsprogramm"

SCAN_DIRS = [
    # (relative_path, extension, category, max_depth)
    ("react-pc-frontend/src/pages", ".tsx", "frontend-page", 3),
    ("react-pc-frontend/src/components", ".tsx", "frontend-component", 4),
    ("react-pc-frontend/src/components", ".ts", "frontend-component", 4),
    ("react-pc-frontend/src/lib", ".ts", "frontend-util", 2),
    ("react-zeiterfassung/src/pages", ".tsx", "zeiterfassung-page", 3),
    ("react-zeiterfassung/src/components", ".tsx", "zeiterfassung-component", 3),
    (f"{JAVA_BASE}/controller", ".java", "backend-controller", 3),
    (f"{JAVA_BASE}/service", ".java", "backend-service", 3),
    (f"{JAVA_BASE}/domain", ".java", "backend-entity", 3),
    (f"{JAVA_BASE}/dto", ".java", "backend-dto", 4),
    (f"{JAVA_BASE}/mapper", ".java", "backend-mapper", 2),
    (f"{JAVA_BASE}/config", ".java", "backend-config", 2),
    (f"{JAVA_BASE}/util", ".java", "backend-util", 2),
    ("docs", ".md", "documentation", 3),
]

SINGLE_FILES = [
    ("react-pc-frontend/src/App.tsx", "frontend-routing"),
    ("react-pc-frontend/src/main.tsx", "frontend-entry"),
    ("react-pc-frontend/src/types.ts", "frontend-types"),
    ("react-zeiterfassung/src/App.tsx", "zeiterfassung-routing"),
]

# Patterns to skip
SKIP_PATTERNS = [re.compile(p) for p in [
    r"Test\.java$", r"IT\.java$", r"\.test\.tsx?$",
    r"node_modules", r"__tests__", r"\.d\.ts$",
]]

# Secret sanitization
SECRET_RE = re.compile(
    r"((?:password|passwd|secret|api[._-]?key|token|credentials)\s*[=:]\s*)([^\s,;\"'}{]+)",
    re.IGNORECASE,
)


@dataclass
class CodeChunk:
    """A single indexed chunk of source code."""
    content: str
    file_path: str       # Relative to project root
    category: str        # e.g. "backend-controller", "frontend-page"
    chunk_type: str      # e.g. "class", "method", "component", "section"
    name: str            # e.g. "AngebotController", "handleSave"
    start_line: int = 0
    end_line: int = 0
    metadata: dict = field(default_factory=dict)

    @property
    def chunk_id(self) -> str:
        raw = f"{self.file_path}::{self.chunk_type}::{self.name}::{self.start_line}"
        return hashlib.md5(raw.encode()).hexdigest()

    @property
    def embedding_text(self) -> str:
        """Text sent to the embedding model, enriched with metadata."""
        header = f"[{self.category}] {self.file_path} — {self.chunk_type}: {self.name}"
        return f"{header}\n\n{self.content}"


# ── Code Chunking ──────────────────────────────────────────────

def chunk_java_file(content: str, file_path: str, category: str) -> list[CodeChunk]:
    """Split a Java file into class-level and method-level chunks."""
    chunks = []
    lines = content.split("\n")
    file_name = Path(file_path).stem

    # Try to extract the full class as one chunk if small enough
    if len(content) <= MAX_CHUNK_CHARS:
        chunks.append(CodeChunk(
            content=content,
            file_path=file_path,
            category=category,
            chunk_type="class",
            name=file_name,
            start_line=1,
            end_line=len(lines),
        ))
        return chunks

    # For larger files: split by methods
    # First: class header (imports + class declaration + fields)
    method_pattern = re.compile(
        r"^(\s{4}(?:public|private|protected)\s+(?:static\s+)?(?:[\w<>\[\],\s]+)\s+"
        r"(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w,\s]+)?\s*\{)",
        re.MULTILINE,
    )

    method_starts = [(m.start(), m.group(2)) for m in method_pattern.finditer(content)]

    if not method_starts:
        # No methods found — index as single chunk (truncated if needed)
        chunks.append(CodeChunk(
            content=content[:MAX_CHUNK_CHARS],
            file_path=file_path,
            category=category,
            chunk_type="class",
            name=file_name,
            start_line=1,
            end_line=len(lines),
        ))
        return chunks

    # Class header: everything before the first method
    header_end = method_starts[0][0]
    header = content[:header_end].rstrip()
    if len(header) >= MIN_CHUNK_CHARS:
        header_lines = header.count("\n") + 1
        chunks.append(CodeChunk(
            content=header,
            file_path=file_path,
            category=category,
            chunk_type="class-header",
            name=f"{file_name} (header)",
            start_line=1,
            end_line=header_lines,
        ))

    # Each method
    for i, (start_pos, method_name) in enumerate(method_starts):
        end_pos = method_starts[i + 1][0] if i + 1 < len(method_starts) else len(content)
        method_text = content[start_pos:end_pos].rstrip()
        start_line = content[:start_pos].count("\n") + 1
        end_line = start_line + method_text.count("\n")

        if len(method_text) > MAX_CHUNK_CHARS:
            method_text = method_text[:MAX_CHUNK_CHARS]

        if len(method_text) >= MIN_CHUNK_CHARS:
            chunks.append(CodeChunk(
                content=method_text,
                file_path=file_path,
                category=category,
                chunk_type="method",
                name=f"{file_name}.{method_name}",
                start_line=start_line,
                end_line=end_line,
            ))

    return chunks


def chunk_react_file(content: str, file_path: str, category: str) -> list[CodeChunk]:
    """Split a React/TS file into component-level chunks."""
    chunks = []
    lines = content.split("\n")
    file_name = Path(file_path).stem

    # Small files: index as single chunk
    if len(content) <= MAX_CHUNK_CHARS:
        # Detect component name from export
        comp_match = re.search(
            r"export\s+(?:default\s+)?function\s+(\w+)", content
        )
        name = comp_match.group(1) if comp_match else file_name

        chunks.append(CodeChunk(
            content=content,
            file_path=file_path,
            category=category,
            chunk_type="component",
            name=name,
            start_line=1,
            end_line=len(lines),
        ))
        return chunks

    # For larger files: split by exported functions / components
    func_pattern = re.compile(
        r"^(export\s+(?:default\s+)?(?:function|const)\s+(\w+))",
        re.MULTILINE,
    )

    func_starts = [(m.start(), m.group(2)) for m in func_pattern.finditer(content)]

    if not func_starts:
        chunks.append(CodeChunk(
            content=content[:MAX_CHUNK_CHARS],
            file_path=file_path,
            category=category,
            chunk_type="component",
            name=file_name,
            start_line=1,
            end_line=len(lines),
        ))
        return chunks

    # Imports block
    imports_end = func_starts[0][0]
    imports = content[:imports_end].rstrip()
    if len(imports) >= MIN_CHUNK_CHARS:
        chunks.append(CodeChunk(
            content=imports,
            file_path=file_path,
            category=category,
            chunk_type="imports",
            name=f"{file_name} (imports)",
            start_line=1,
            end_line=imports.count("\n") + 1,
        ))

    # Each function/component
    for i, (start_pos, func_name) in enumerate(func_starts):
        end_pos = func_starts[i + 1][0] if i + 1 < len(func_starts) else len(content)
        func_text = content[start_pos:end_pos].rstrip()
        start_line = content[:start_pos].count("\n") + 1
        end_line = start_line + func_text.count("\n")

        # Prepend imports for context
        chunk_content = func_text
        if len(imports) + len(func_text) + 2 <= MAX_CHUNK_CHARS and imports:
            chunk_content = imports + "\n\n" + func_text

        if len(chunk_content) > MAX_CHUNK_CHARS:
            chunk_content = chunk_content[:MAX_CHUNK_CHARS]

        if len(func_text) >= MIN_CHUNK_CHARS:
            chunks.append(CodeChunk(
                content=chunk_content,
                file_path=file_path,
                category=category,
                chunk_type="component",
                name=func_name,
                start_line=start_line,
                end_line=end_line,
            ))

    return chunks


def chunk_markdown_file(content: str, file_path: str, category: str) -> list[CodeChunk]:
    """Split markdown by ## headings."""
    chunks = []
    file_name = Path(file_path).stem

    if len(content) <= MAX_CHUNK_CHARS:
        chunks.append(CodeChunk(
            content=content,
            file_path=file_path,
            category=category,
            chunk_type="document",
            name=file_name,
            start_line=1,
            end_line=content.count("\n") + 1,
        ))
        return chunks

    sections = re.split(r"^(## .+)$", content, flags=re.MULTILINE)

    # Preamble (before first ##)
    if sections[0].strip():
        chunks.append(CodeChunk(
            content=sections[0][:MAX_CHUNK_CHARS],
            file_path=file_path,
            category=category,
            chunk_type="section",
            name=f"{file_name} (intro)",
            start_line=1,
            end_line=sections[0].count("\n") + 1,
        ))

    # Paired headings + content
    for i in range(1, len(sections), 2):
        heading = sections[i].strip()
        body = sections[i + 1] if i + 1 < len(sections) else ""
        section_text = f"{heading}\n{body}".strip()

        if len(section_text) < MIN_CHUNK_CHARS:
            continue

        section_name = heading.lstrip("#").strip()
        chunks.append(CodeChunk(
            content=section_text[:MAX_CHUNK_CHARS],
            file_path=file_path,
            category=category,
            chunk_type="section",
            name=f"{file_name} — {section_name}",
            start_line=0,
            end_line=0,
        ))

    return chunks


def chunk_file(content: str, file_path: str, category: str) -> list[CodeChunk]:
    """Route to the appropriate chunker based on file extension."""
    if file_path.endswith(".java"):
        return chunk_java_file(content, file_path, category)
    elif file_path.endswith((".tsx", ".ts")):
        return chunk_react_file(content, file_path, category)
    elif file_path.endswith(".md"):
        return chunk_markdown_file(content, file_path, category)
    else:
        return [CodeChunk(
            content=content[:MAX_CHUNK_CHARS],
            file_path=file_path,
            category=category,
            chunk_type="file",
            name=Path(file_path).stem,
        )]


# ── File Scanning ──────────────────────────────────────────────

def should_skip(path: Path) -> bool:
    path_str = str(path)
    return any(p.search(path_str) for p in SKIP_PATTERNS)


def sanitize_secrets(content: str) -> str:
    return SECRET_RE.sub(r"\1***REDACTED***", content)


def scan_directory(root: Path, rel_path: str, ext: str, category: str, max_depth: int) -> list[CodeChunk]:
    """Scan a directory for files and chunk them."""
    dir_path = root / rel_path
    if not dir_path.is_dir():
        return []

    chunks = []
    for path in sorted(dir_path.rglob(f"*{ext}")):
        # Enforce max depth
        try:
            relative = path.relative_to(dir_path)
            if len(relative.parts) > max_depth:
                continue
        except ValueError:
            continue

        if should_skip(path):
            continue

        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue

        content = sanitize_secrets(content)
        rel_file = str(path.relative_to(root)).replace("\\", "/")
        chunks.extend(chunk_file(content, rel_file, category))

    return chunks


def scan_codebase(root: Path) -> list[CodeChunk]:
    """Scan the entire codebase and return all chunks."""
    all_chunks = []

    for rel_path, ext, category, max_depth in SCAN_DIRS:
        dir_chunks = scan_directory(root, rel_path, ext, category, max_depth)
        all_chunks.extend(dir_chunks)
        if dir_chunks:
            print(f"  {category}: {len(dir_chunks)} chunks from {rel_path}")

    for rel_path, category in SINGLE_FILES:
        file_path = root / rel_path
        if file_path.is_file():
            try:
                content = file_path.read_text(encoding="utf-8", errors="replace")
                content = sanitize_secrets(content)
                rel_file = rel_path.replace("\\", "/")
                file_chunks = chunk_file(content, rel_file, category)
                all_chunks.extend(file_chunks)
                if file_chunks:
                    print(f"  {category}: {len(file_chunks)} chunks from {rel_path}")
            except OSError:
                pass

    return all_chunks


# ── Embedding ──────────────────────────────────────────────────

def embed_chunks(chunks: list[CodeChunk], api_key: str) -> list[list[float]]:
    """Generate embeddings for all chunks using Gemini text-embedding-004."""
    genai.configure(api_key=api_key)

    all_embeddings = []
    texts = [c.embedding_text for c in chunks]

    for i in range(0, len(texts), BATCH_SIZE):
        batch = texts[i : i + BATCH_SIZE]
        print(f"  Embedding batch {i // BATCH_SIZE + 1}/{(len(texts) - 1) // BATCH_SIZE + 1} "
              f"({len(batch)} chunks)...")

        result = genai.embed_content(
            model=GEMINI_EMBEDDING_MODEL,
            content=batch,
            task_type="RETRIEVAL_DOCUMENT",
        )
        all_embeddings.extend(result["embedding"])

        # Rate limiting: Gemini free tier = 1500 RPM
        if i + BATCH_SIZE < len(texts):
            time.sleep(1)

    return all_embeddings


def embed_query(query: str, api_key: str) -> list[float]:
    """Embed a single query for searching."""
    genai.configure(api_key=api_key)
    result = genai.embed_content(
        model=GEMINI_EMBEDDING_MODEL,
        content=query,
        task_type="RETRIEVAL_QUERY",
    )
    return result["embedding"]


# ── Qdrant Storage ─────────────────────────────────────────────

def init_qdrant(collection: str, recreate: bool = True) -> QdrantClient:
    """Connect to Qdrant and create/recreate the collection."""
    client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)

    if recreate:
        client.recreate_collection(
            collection_name=collection,
            vectors_config=VectorParams(
                size=EMBEDDING_DIMENSION,
                distance=Distance.COSINE,
            ),
        )
        print(f"  Collection '{collection}' created (dimension={EMBEDDING_DIMENSION})")
    return client


def upload_to_qdrant(
    client: QdrantClient,
    collection: str,
    chunks: list[CodeChunk],
    embeddings: list[list[float]],
):
    """Upload embedded chunks to Qdrant."""
    points = []
    for i, (chunk, embedding) in enumerate(zip(chunks, embeddings)):
        points.append(PointStruct(
            id=i,
            vector=embedding,
            payload={
                "content": chunk.content,
                "file_path": chunk.file_path,
                "category": chunk.category,
                "chunk_type": chunk.chunk_type,
                "name": chunk.name,
                "start_line": chunk.start_line,
                "end_line": chunk.end_line,
                "chunk_id": chunk.chunk_id,
            },
        ))

    # Upload in batches
    batch_size = 100
    for i in range(0, len(points), batch_size):
        batch = points[i : i + batch_size]
        client.upsert(collection_name=collection, points=batch)
        print(f"  Uploaded {min(i + batch_size, len(points))}/{len(points)} points")


# ── Main ───────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Index codebase into Qdrant for KI-Hilfe RAG")
    parser.add_argument("--collection", default=DEFAULT_COLLECTION, help="Qdrant collection name")
    parser.add_argument("--dry-run", action="store_true", help="Only scan and chunk, skip embedding/upload")
    parser.add_argument("--api-key", default=None, help="Gemini API key (or set GEMINI_API_KEY env var)")
    args = parser.parse_args()

    api_key = args.api_key or os.environ.get("GEMINI_API_KEY")
    if not api_key and not args.dry_run:
        print("ERROR: Gemini API key required. Set GEMINI_API_KEY env var or use --api-key")
        sys.exit(1)

    print(f"[1/4] Scanning codebase at {PROJECT_ROOT}...")
    chunks = scan_codebase(PROJECT_ROOT)
    print(f"  Total: {len(chunks)} chunks")

    if not chunks:
        print("ERROR: No chunks found. Check project structure.")
        sys.exit(1)

    if args.dry_run:
        print("\n[DRY RUN] Chunk summary:")
        by_category = {}
        for c in chunks:
            by_category.setdefault(c.category, []).append(c)
        for cat, cat_chunks in sorted(by_category.items()):
            print(f"\n  {cat} ({len(cat_chunks)} chunks):")
            for c in cat_chunks[:5]:
                preview = c.content[:80].replace("\n", " ")
                print(f"    - [{c.chunk_type}] {c.name}: {preview}...")
            if len(cat_chunks) > 5:
                print(f"    ... and {len(cat_chunks) - 5} more")
        return

    print(f"\n[2/4] Generating embeddings ({len(chunks)} chunks)...")
    embeddings = embed_chunks(chunks, api_key)

    print(f"\n[3/4] Initializing Qdrant collection '{args.collection}'...")
    client = init_qdrant(args.collection)

    print(f"\n[4/4] Uploading to Qdrant...")
    upload_to_qdrant(client, args.collection, chunks, embeddings)

    print(f"\nDone! {len(chunks)} chunks indexed in collection '{args.collection}'")
    print(f"Qdrant dashboard: http://{QDRANT_HOST}:{QDRANT_PORT}/dashboard")


if __name__ == "__main__":
    main()
