import React, { useCallback, useEffect, useRef } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import TextAlign from '@tiptap/extension-text-align';
import Highlight from '@tiptap/extension-highlight';
import { TextStyle } from '@tiptap/extension-text-style';
import { Extension } from '@tiptap/core';
import Color from '@tiptap/extension-color';
import Underline from '@tiptap/extension-underline';
import {
    AlignCenter,
    AlignJustify,
    AlignLeft,
    AlignRight,
    Bold,
    CaseSensitive,
    Highlighter,
    Image as ImageIcon,
    Italic,
    List,
    ListOrdered,
    Type,
    Underline as UnderlineIcon,
    X,
    Undo,
    Redo
} from 'lucide-react';
import { Button } from './ui/button';

// Font Size options (in pt) - begrenzt auf 10-20pt für konsistente PDF-Ausgabe
const FONT_SIZES = ['10', '11', '12', '14', '16', '18', '20'];

// Custom FontSize extension
const FontSize = Extension.create({
    name: 'fontSize',
    addOptions() {
        return {
            types: ['textStyle'],
        };
    },
    addGlobalAttributes() {
        return [
            {
                types: this.options.types,
                attributes: {
                    fontSize: {
                        default: null,
                        parseHTML: element => element.style.fontSize?.replace(/['"]+/g, ''),
                        renderHTML: attributes => {
                            if (!attributes.fontSize) {
                                return {};
                            }
                            return {
                                style: `font-size: ${attributes.fontSize}`,
                            };
                        },
                    },
                },
            },
        ];
    },
    addCommands() {
        return {
            setFontSize: (fontSize: string) => ({ chain }: { chain: () => any }) => {
                return chain().setMark('textStyle', { fontSize }).run();
            },
            unsetFontSize: () => ({ chain }: { chain: () => any }) => {
                return chain().setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run();
            },
        } as any;
    },
});

interface TiptapEditorProps {
    value: string;
    onChange: (value: string) => void;
    hideToolbar?: boolean;
    compactMode?: boolean; // For document builder - auto-sizes to content height
    readOnly?: boolean;
    onFocus?: () => void;
    onEditorReady?: (editor: ReturnType<typeof useEditor>) => void;
}

export interface TiptapEditorRef {
    editor: ReturnType<typeof useEditor> | null;
}

// Custom Image extension with resizing and drag-and-drop repositioning
const ResizableImage = Image.extend({
    selectable: true,
    draggable: true,

    addAttributes() {
        return {
            ...this.parent?.(),
            width: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('width') || element.style.width?.replace('px', ''),
                renderHTML: (attributes: Record<string, unknown>) => {
                    const w = attributes.width || 300;
                    return { width: w, style: `width: ${w}px` };
                },
            },
            height: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('height') || element.style.height?.replace('px', ''),
                renderHTML: (attributes: Record<string, unknown>) => {
                    if (!attributes.height) return {};
                    return { height: attributes.height, style: `height: ${attributes.height}px` };
                },
            },
        };
    },
    addNodeView() {
        return ({ node, getPos, editor }) => {
            // ── Wrapper ──
            const dom = document.createElement('div');
            dom.className = 'tiptap-image-wrapper';
            dom.draggable = true;

            let currentNode = node;
            let selected = false;
            let isResizing = false;

            // ── Persistent image element ──
            const img = document.createElement('img');
            img.className = 'tiptap-image-el';
            img.draggable = false;
            dom.appendChild(img);

            // ── Mouse-down: select node, allow native drag to start ──
            dom.addEventListener('mousedown', () => {
                if (isResizing) return; // don't interfere with resize handles
                const pos = typeof getPos === 'function' ? getPos() : undefined;
                if (pos !== undefined) {
                    // Do NOT preventDefault so the browser can initiate a native drag
                    editor.chain().focus().setNodeSelection(pos).run();
                }
            });

            // ── Drag start: custom ghost ──
            dom.addEventListener('dragstart', (e) => {
                dom.classList.add('tiptap-image-dragging');
                // Create a nice ghost image
                const ghost = img.cloneNode(true) as HTMLImageElement;
                ghost.style.cssText = `width:${img.offsetWidth * 0.6}px;opacity:0.85;border-radius:8px;box-shadow:0 12px 40px rgba(0,0,0,0.25);position:absolute;top:-9999px;`;
                document.body.appendChild(ghost);
                e.dataTransfer?.setDragImage(ghost, ghost.offsetWidth / 2, 20);
                requestAnimationFrame(() => document.body.removeChild(ghost));
            });

            dom.addEventListener('dragend', () => {
                dom.classList.remove('tiptap-image-dragging');
                dom.classList.add('tiptap-image-dropped');
                dom.addEventListener('animationend', () => dom.classList.remove('tiptap-image-dropped'), { once: true });
            });

            // ── Render / update visuals ──
            const renderDOM = () => {
                const width = currentNode.attrs.width || 300;
                const height = currentNode.attrs.height;

                // Remove old handles/buttons (everything after img)
                while (dom.lastChild && dom.lastChild !== img) {
                    dom.removeChild(dom.lastChild);
                }

                dom.style.width = typeof width === 'number' ? `${width}px` : String(width);
                dom.classList.toggle('tiptap-image-selected', selected);

                img.src = currentNode.attrs.src;
                img.alt = currentNode.attrs.alt || '';
                img.style.height = height ? `${height}px` : 'auto';

                if (!selected) return;

                // ── Resize handles ──
                const handles = [
                    { mode: 'tl', top: '-6px', left: '-6px', cursor: 'nwse-resize' },
                    { mode: 'tr', top: '-6px', right: '-6px', cursor: 'nesw-resize' },
                    { mode: 'bl', bottom: '-6px', left: '-6px', cursor: 'nesw-resize' },
                    { mode: 'br', bottom: '-6px', right: '-6px', cursor: 'nwse-resize' },
                    { mode: 'l', top: '50%', left: '-6px', transform: 'translateY(-50%)', cursor: 'ew-resize' },
                    { mode: 'r', top: '50%', right: '-6px', transform: 'translateY(-50%)', cursor: 'ew-resize' },
                    { mode: 't', top: '-6px', left: '50%', transform: 'translateX(-50%)', cursor: 'ns-resize' },
                    { mode: 'b', bottom: '-6px', left: '50%', transform: 'translateX(-50%)', cursor: 'ns-resize' },
                ] as const;

                handles.forEach(h => {
                    const handle = document.createElement('span');
                    handle.className = 'tiptap-image-handle';
                    handle.style.cssText = `cursor:${h.cursor};
                        ${'top' in h && h.top ? `top:${h.top};` : ''}
                        ${'bottom' in h && h.bottom ? `bottom:${h.bottom};` : ''}
                        ${'left' in h && h.left ? `left:${h.left};` : ''}
                        ${'right' in h && h.right ? `right:${h.right};` : ''}
                        ${'transform' in h && h.transform ? `transform:${h.transform};` : ''}`;

                    let startX = 0, startY = 0, startW = 0, startH = 0;

                    handle.addEventListener('mousedown', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        isResizing = true;
                        dom.draggable = false; // disable drag while resizing
                        startX = e.clientX;
                        startY = e.clientY;
                        const rect = dom.getBoundingClientRect();
                        startW = rect.width;
                        startH = rect.height;

                        const onMouseMove = (ev: MouseEvent) => {
                            const dx = ev.clientX - startX;
                            const dy = ev.clientY - startY;
                            const aspect = startW / startH;
                            let newW = startW, newH = startH;
                            const min = 50;

                            switch (h.mode) {
                                case 'br': case 'tr':
                                    newW = Math.max(min, startW + dx);
                                    newH = newW / aspect;
                                    break;
                                case 'bl': case 'tl':
                                    newW = Math.max(min, startW - dx);
                                    newH = newW / aspect;
                                    break;
                                case 'r':
                                    newW = Math.max(min, startW + dx);
                                    break;
                                case 'l':
                                    newW = Math.max(min, startW - dx);
                                    break;
                                case 'b':
                                    newH = Math.max(min, startH + dy);
                                    break;
                                case 't':
                                    newH = Math.max(min, startH - dy);
                                    break;
                            }

                            const currentPos = typeof getPos === 'function' ? getPos() : undefined;
                            if (currentPos !== undefined) {
                                editor.chain().focus().setNodeSelection(currentPos).updateAttributes('image', {
                                    width: Math.round(newW),
                                    height: Math.round(newH),
                                }).run();
                            }
                        };

                        const onMouseUp = () => {
                            isResizing = false;
                            dom.draggable = true;
                            document.removeEventListener('mousemove', onMouseMove);
                            document.removeEventListener('mouseup', onMouseUp);
                        };

                        document.addEventListener('mousemove', onMouseMove);
                        document.addEventListener('mouseup', onMouseUp);
                    });

                    dom.appendChild(handle);
                });

                // ── Delete button ──
                const deleteBtn = document.createElement('button');
                deleteBtn.className = 'tiptap-image-delete';
                deleteBtn.innerHTML = '×';
                deleteBtn.addEventListener('mousedown', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    const currentPos = typeof getPos === 'function' ? getPos() : undefined;
                    if (currentPos !== undefined) {
                        editor.commands.deleteRange({ from: currentPos, to: currentPos + currentNode.nodeSize });
                    }
                });
                dom.appendChild(deleteBtn);
            };

            renderDOM();

            return {
                dom,
                update: (updatedNode) => {
                    if (updatedNode.type.name !== 'image') return false;
                    currentNode = updatedNode;
                    renderDOM();
                    return true;
                },
                selectNode: () => {
                    selected = true;
                    renderDOM();
                },
                deselectNode: () => {
                    selected = false;
                    renderDOM();
                },
                stopEvent: (event: Event) => {
                    const t = event.type;
                    // Let keyboard through (Delete/Backspace to remove image)
                    if (t === 'keydown' || t === 'keyup' || t === 'keypress') return false;
                    // Let drag/drop through to ProseMirror for repositioning
                    if (t.startsWith('drag') || t === 'drop') return false;
                    // We handle mouse events (selection, resize, delete)
                    return true;
                },
                ignoreMutation: () => true,
            };
        };
    },
});

// Toolbar button component
const ToolbarButton: React.FC<{
    onClick: () => void;
    active?: boolean;
    disabled?: boolean;
    children: React.ReactNode;
    title?: string;
}> = ({ onClick, active, disabled, children, title }) => (
    <Button
        variant="ghost"
        size="sm"
        className={`text-rose-700 hover:bg-rose-100 ${active ? 'bg-rose-100' : ''}`}
        onClick={onClick}
        disabled={disabled}
        title={title}
    >
        {children}
    </Button>
);

// Exportable standalone Toolbar component
export const TiptapToolbar: React.FC<{ editor: ReturnType<typeof useEditor> | null }> = ({ editor }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleImageUpload = useCallback(() => {
        fileInputRef.current?.click();
    }, []);

    const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || !editor) return;

        Array.from(files).forEach(file => {
            if (file.type.startsWith('image/')) {
                const reader = new FileReader();
                reader.onload = (ev) => {
                    const src = ev.target?.result as string;
                    if (src) {
                        editor.chain().focus().setImage({ src } as { src: string }).run();
                    }
                };
                reader.readAsDataURL(file);
            }
        });

        e.target.value = '';
    }, [editor]);

    if (!editor) {
        return (
            <div className="flex flex-wrap gap-1 items-center bg-slate-100 border border-slate-200 rounded-lg px-3 py-2">
                <span className="text-slate-400 text-sm">Klicken Sie auf einen Textblock zum Bearbeiten...</span>
            </div>
        );
    }

    return (
        <div className="flex flex-wrap gap-1 items-center bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
            {/* Undo/Redo */}
            <ToolbarButton
                onClick={() => editor.chain().focus().undo().run()}
                disabled={!editor.can().undo()}
                title="Rükgängig (Ctrl+Z)"
            >
                <Undo className="w-4 h-4" />
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().redo().run()}
                disabled={!editor.can().redo()}
                title="Wiederholen (Ctrl+Y)"
            >
                <Redo className="w-4 h-4" />
            </ToolbarButton>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Text formatting */}
            <ToolbarButton
                onClick={() => editor.chain().focus().toggleBold().run()}
                active={editor.isActive('bold')}
                title="Fett (Ctrl+B)"
            >
                <Bold className="w-4 h-4" /> Fett
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().toggleItalic().run()}
                active={editor.isActive('italic')}
                title="Kursiv (Ctrl+I)"
            >
                <Italic className="w-4 h-4" /> Kursiv
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().toggleUnderline().run()}
                active={editor.isActive('underline')}
                title="Unterstrichen (Ctrl+U)"
            >
                <UnderlineIcon className="w-4 h-4" /> Unterstrichen
            </ToolbarButton>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Font Size */}
            <label className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100">
                <CaseSensitive className="w-4 h-4" />
                Größe
                <select
                    className="border border-rose-200 rounded px-2 py-0.5 text-sm bg-white cursor-pointer focus:outline-none focus:ring-1 focus:ring-rose-300"
                    value=""
                    onChange={(e) => {
                        if (e.target.value) {
                            (editor.commands as any).setFontSize(`${e.target.value}pt`);
                        }
                    }}
                >
                    <option value="">—</option>
                    {FONT_SIZES.map((size) => (
                        <option key={size} value={size}>{size}pt</option>
                    ))}
                </select>
            </label>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Lists */}
            <ToolbarButton
                onClick={() => editor.chain().focus().toggleBulletList().run()}
                active={editor.isActive('bulletList')}
                title="Aufzählung"
            >
                <List className="w-4 h-4" /> Liste
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().toggleOrderedList().run()}
                active={editor.isActive('orderedList')}
                title="Nummerierte Liste"
            >
                <ListOrdered className="w-4 h-4" /> Nummeriert
            </ToolbarButton>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Alignment */}
            <ToolbarButton
                onClick={() => editor.chain().focus().setTextAlign('left').run()}
                active={editor.isActive({ textAlign: 'left' })}
                title="Linksbündig"
            >
                <AlignLeft className="w-4 h-4" />
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().setTextAlign('center').run()}
                active={editor.isActive({ textAlign: 'center' })}
                title="Zentriert"
            >
                <AlignCenter className="w-4 h-4" />
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().setTextAlign('right').run()}
                active={editor.isActive({ textAlign: 'right' })}
                title="Rechtsbündig"
            >
                <AlignRight className="w-4 h-4" />
            </ToolbarButton>
            <ToolbarButton
                onClick={() => editor.chain().focus().setTextAlign('justify').run()}
                active={editor.isActive({ textAlign: 'justify' })}
                title="Blocksatz"
            >
                <AlignJustify className="w-4 h-4" />
            </ToolbarButton>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Colors */}
            <label 
                className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100"
                onMouseDown={(e) => e.preventDefault()} // Verhindert Fokus-Verlust vom Editor
            >
                <Type className="w-4 h-4" />
                Textfarbe
                <input
                    type="color"
                    className="w-8 h-6 border border-rose-200 rounded cursor-pointer"
                    defaultValue="#be123c"
                    onMouseDown={(e) => e.stopPropagation()}
                    onChange={(e) => {
                        // Farbe setzen ohne Fokus zu ändern - behält Selektion bei
                        editor.chain().setColor(e.target.value).run();
                    }}
                />
            </label>
            <label 
                className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100"
                onMouseDown={(e) => e.preventDefault()} // Verhindert Fokus-Verlust vom Editor
            >
                <Highlighter className="w-4 h-4" />
                Hintergrund
                <input
                    type="color"
                    className="w-8 h-6 border border-rose-200 rounded cursor-pointer"
                    defaultValue="#fee2e2"
                    onMouseDown={(e) => e.stopPropagation()}
                    onChange={(e) => {
                        // Highlight setzen ohne Fokus zu ändern - behält Selektion bei
                        editor.chain().toggleHighlight({ color: e.target.value }).run();
                    }}
                />
            </label>

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Image */}
            <ToolbarButton onClick={handleImageUpload} title="Bild einfügen">
                <ImageIcon className="w-4 h-4" /> Bild
            </ToolbarButton>
            <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFileChange}
                multiple
            />

            <span className="w-px h-6 bg-rose-200 mx-1" />

            {/* Clear formatting */}
            <ToolbarButton
                onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}
                title="Formatierung entfernen"
            >
                <X className="w-4 h-4" /> Reset
            </ToolbarButton>
        </div>
    );
};

export const TiptapEditor: React.FC<TiptapEditorProps> = ({ value, onChange, hideToolbar = false, compactMode = false, readOnly = false, onFocus, onEditorReady }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const editorContainerRef = useRef<HTMLDivElement>(null);
    /** Tracks the value the editor was initialized with so the first sync can be skipped
     *  (avoids an unnecessary setContent → getHTML round-trip that can normalize away
     *  inline styles such as font-size, color, text-align, etc.)  */
    const editorInitValueRef = useRef(value);
    const editorReadyRef = useRef(false);

    const editor = useEditor({
        extensions: [
            StarterKit.configure({
                heading: false,
            }),
            Underline,
            TextStyle,
            Color,
            FontSize,
            Highlight.configure({ multicolor: true }),
            TextAlign.configure({
                types: ['paragraph', 'heading'],
            }),
            ResizableImage.configure({
                inline: true,
                allowBase64: true,
            }),
        ],
        content: value,
        editable: !readOnly,
        onUpdate: ({ editor: ed }) => {
            onChange(ed.getHTML());
        },
        editorProps: {
            attributes: {
                class: `tiptap-editor-content prose prose-sm max-w-none focus:outline-none ${compactMode ? 'min-h-[2.5rem]' : 'min-h-[200px]'} p-3`,
            },
            handleDrop: (view, event, _slice, moved) => {
                if (!moved && event.dataTransfer?.files?.length) {
                    const files = Array.from(event.dataTransfer.files);
                    const images = files.filter(file => file.type.startsWith('image/'));

                    if (images.length > 0) {
                        event.preventDefault();

                        images.forEach(file => {
                            const reader = new FileReader();
                            reader.onload = (e) => {
                                const src = e.target?.result as string;
                                if (src) {
                                    const { schema } = view.state;
                                    const coordinates = view.posAtCoords({ left: event.clientX, top: event.clientY });
                                    const imgNode = schema.nodes.image.create({ src, width: 300 });
                                    if (coordinates) {
                                        const transaction = view.state.tr.insert(coordinates.pos, imgNode);
                                        view.dispatch(transaction);
                                    }
                                }
                            };
                            reader.readAsDataURL(file);
                        });

                        return true;
                    }
                }
                return false;
            },
            handlePaste: (_view, event) => {
                const items = event.clipboardData?.items;
                if (!items) return false;

                for (const item of Array.from(items)) {
                    if (item.type.startsWith('image/')) {
                        event.preventDefault();
                        const file = item.getAsFile();
                        if (file) {
                            const reader = new FileReader();
                            reader.onload = (e) => {
                                const src = e.target?.result as string;
                                if (src && editor) {
                                    editor.chain().focus().setImage({ src } as { src: string }).run();
                                }
                            };
                            reader.readAsDataURL(file);
                        }
                        return true;
                    }
                }
                return false;
            },
        },
    });

    // Sync external value changes – skip the first fire to preserve rich-text
    // formatting that was already set via the useEditor({ content }) initialisation.
    useEffect(() => {
        if (!editor) return;

        if (!editorReadyRef.current) {
            editorReadyRef.current = true;
            // If the value changed between the useEditor() call and now, sync it.
            if (value !== editorInitValueRef.current && value !== editor.getHTML()) {
                editor.commands.setContent(value);
            }
            return;
        }

        if (value !== editor.getHTML()) {
            editor.commands.setContent(value);
        }
    }, [value, editor]);

    useEffect(() => {
        if (editor) {
            editor.setEditable(!readOnly);
        }
    }, [editor, readOnly]);

    // Notify parent when editor is ready
    useEffect(() => {
        if (editor && onEditorReady) {
            onEditorReady(editor);
        }
    }, [editor, onEditorReady]);

    const handleImageUpload = useCallback(() => {
        fileInputRef.current?.click();
    }, []);

    const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || !editor) return;

        Array.from(files).forEach(file => {
            if (file.type.startsWith('image/')) {
                const reader = new FileReader();
                reader.onload = (ev) => {
                    const src = ev.target?.result as string;
                    if (src) {
                        editor.chain().focus().setImage({ src } as { src: string }).run();
                    }
                };
                reader.readAsDataURL(file);
            }
        });

        e.target.value = '';
    }, [editor]);

    if (!editor) {
        return <div className="min-h-[260px] border border-slate-200 rounded-lg p-3 bg-white animate-pulse" />;
    }

    return (
        <div className="space-y-2">
            {/* Toolbar - conditionally shown */}
            {!hideToolbar && !readOnly && (
                <div className="flex flex-wrap gap-1 items-center bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
                    {/* Undo/Redo */}
                    <ToolbarButton
                        onClick={() => editor.chain().focus().undo().run()}
                        disabled={!editor.can().undo()}
                        title="Rückgängig (Ctrl+Z)"
                    >
                        <Undo className="w-4 h-4" />
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().redo().run()}
                        disabled={!editor.can().redo()}
                        title="Wiederholen (Ctrl+Y)"
                    >
                        <Redo className="w-4 h-4" />
                    </ToolbarButton>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Text formatting */}
                    <ToolbarButton
                        onClick={() => editor.chain().focus().toggleBold().run()}
                        active={editor.isActive('bold')}
                        title="Fett (Ctrl+B)"
                    >
                        <Bold className="w-4 h-4" /> Fett
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().toggleItalic().run()}
                        active={editor.isActive('italic')}
                        title="Kursiv (Ctrl+I)"
                    >
                        <Italic className="w-4 h-4" /> Kursiv
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().toggleUnderline().run()}
                        active={editor.isActive('underline')}
                        title="Unterstrichen (Ctrl+U)"
                    >
                        <UnderlineIcon className="w-4 h-4" /> Unterstrichen
                    </ToolbarButton>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Font Size */}
                    <label className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100">
                        <CaseSensitive className="w-4 h-4" />
                        Größe
                        <select
                            className="border border-rose-200 rounded px-2 py-0.5 text-sm bg-white cursor-pointer focus:outline-none focus:ring-1 focus:ring-rose-300"
                            value=""
                            onChange={(e) => {
                                if (e.target.value) {
                                    (editor.commands as any).setFontSize(`${e.target.value}pt`);
                                }
                            }}
                        >
                            <option value="">—</option>
                            {FONT_SIZES.map((size) => (
                                <option key={size} value={size}>{size}pt</option>
                            ))}
                        </select>
                    </label>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Lists */}
                    <ToolbarButton
                        onClick={() => editor.chain().focus().toggleBulletList().run()}
                        active={editor.isActive('bulletList')}
                        title="Aufzählung"
                    >
                        <List className="w-4 h-4" /> Liste
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().toggleOrderedList().run()}
                        active={editor.isActive('orderedList')}
                        title="Nummerierte Liste"
                    >
                        <ListOrdered className="w-4 h-4" /> Nummeriert
                    </ToolbarButton>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Alignment */}
                    <ToolbarButton
                        onClick={() => editor.chain().focus().setTextAlign('left').run()}
                        active={editor.isActive({ textAlign: 'left' })}
                        title="Linksbündig"
                    >
                        <AlignLeft className="w-4 h-4" />
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().setTextAlign('center').run()}
                        active={editor.isActive({ textAlign: 'center' })}
                        title="Zentriert"
                    >
                        <AlignCenter className="w-4 h-4" />
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().setTextAlign('right').run()}
                        active={editor.isActive({ textAlign: 'right' })}
                        title="Rechtsbündig"
                    >
                        <AlignRight className="w-4 h-4" />
                    </ToolbarButton>
                    <ToolbarButton
                        onClick={() => editor.chain().focus().setTextAlign('justify').run()}
                        active={editor.isActive({ textAlign: 'justify' })}
                        title="Blocksatz"
                    >
                        <AlignJustify className="w-4 h-4" />
                    </ToolbarButton>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Colors */}
                    <label 
                        className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100"
                        onMouseDown={(e) => e.preventDefault()} // Verhindert Fokus-Verlust vom Editor
                    >
                        <Type className="w-4 h-4" />
                        Textfarbe
                        <input
                            type="color"
                            className="w-8 h-6 border border-rose-200 rounded cursor-pointer"
                            defaultValue="#be123c"
                            onMouseDown={(e) => e.stopPropagation()}
                            onChange={(e) => {
                                // Farbe setzen ohne Fokus zu ändern - behält Selektion bei
                                editor.chain().setColor(e.target.value).run();
                            }}
                        />
                    </label>
                    <label 
                        className="inline-flex items-center gap-2 text-sm text-rose-700 cursor-pointer px-2 py-1 rounded hover:bg-rose-100"
                        onMouseDown={(e) => e.preventDefault()} // Verhindert Fokus-Verlust vom Editor
                    >
                        <Highlighter className="w-4 h-4" />
                        Hintergrund
                        <input
                            type="color"
                            className="w-8 h-6 border border-rose-200 rounded cursor-pointer"
                            defaultValue="#fee2e2"
                            onMouseDown={(e) => e.stopPropagation()}
                            onChange={(e) => {
                                // Highlight setzen ohne Fokus zu ändern - behält Selektion bei
                                editor.chain().toggleHighlight({ color: e.target.value }).run();
                            }}
                        />
                    </label>

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Image */}
                    <ToolbarButton onClick={handleImageUpload} title="Bild einfügen">
                        <ImageIcon className="w-4 h-4" /> Bild
                    </ToolbarButton>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={handleFileChange}
                        multiple
                    />

                    <span className="w-px h-6 bg-rose-200 mx-1" />

                    {/* Clear formatting */}
                    <ToolbarButton
                        onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}
                        title="Formatierung entfernen"
                    >
                        <X className="w-4 h-4" /> Reset
                    </ToolbarButton>
                </div>
            )}

            {/* Hint only when toolbar is visible */}
            {!hideToolbar && (
                <p className="text-xs text-slate-500">
                    Tipp: Bilder per Drag &amp; Drop oder Einfügen (Ctrl+V) hinzufügen. Klicken Sie auf ein Bild, um es zu bearbeiten.
                </p>
            )}

            {/* Editor */}
            <div
                ref={editorContainerRef}
                className={`${compactMode ? '' : 'border border-slate-200 rounded-lg shadow-inner'} bg-white overflow-visible`}
                onDragOver={(e) => e.preventDefault()}
                onFocus={onFocus}
            >
                <EditorContent
                    editor={editor}
                    className={compactMode ? 'tiptap-compact' : ''}
                />
            </div>

            {!compactMode && (
                <p className="text-xs text-slate-500">
                    Tipp: Bilder per Drag & Drop oder Einfügen (Ctrl+V) hinzufügen. Klicken Sie auf ein Bild, um es zu bearbeiten.
                </p>
            )}
        </div>
    );
};

export default TiptapEditor;
