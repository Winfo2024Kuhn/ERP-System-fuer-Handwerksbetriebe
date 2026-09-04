// app.jsx — compose the desktop ERP app

const DesktopApp = () => (
  <div style={{ minHeight: "100vh", background: "var(--slate-50)" }}>
    <Ribbon />
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: "32px 32px 80px" }}>
      <PageHeader
        eyebrow="PROJEKTMANAGEMENT"
        title="PROJEKTÜBERSICHT"
        subtitle="Übersicht und Verwaltung Ihrer Projekte."
        actions={<>
          <Button variant="default" icon="plus">Neues Projekt</Button>
          <Button variant="outline" icon="refresh-cw">Aktualisieren</Button>
        </>}
      />
      <FilterBar />
      <EmptyState />
      <Pagination />
    </main>
    <KIHilfeFab />
  </div>
);

ReactDOM.createRoot(document.getElementById("root")).render(<DesktopApp />);
