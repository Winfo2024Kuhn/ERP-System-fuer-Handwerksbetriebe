import React from 'react';
import { Card } from './ui/card';

interface DetailLayoutProps {
    header: React.ReactNode;
    mainContent: React.ReactNode; // e.g. Email History
    sideContent: React.ReactNode; // e.g. Contact Data, Map
}

export const DetailLayout: React.FC<DetailLayoutProps> = ({ header, mainContent, sideContent }) => {
    return (
        <div className="space-y-6 animate-in fade-in duration-300">
            {/* Header Section */}
            {header}

            {/* Two Column Layout */}
            {/*
              Rasterzellen haben standardmaessig min-width: auto und schrumpfen
              deshalb nicht unter die Mindestinhaltsbreite ihres Inhalts -- bei
              7 nicht umbrechenden Reitern (ProjektEditor.tsx) sind das 1247px,
              mehr als bei 1440px verfuegbar ist. Die linke Spalte konnte damit
              nie unter ihre 3fr schrumpfen, die rechte Spalte (z.B. "Projektdaten")
              wurde dadurch rechts aus dem Fenster gedrueckt (Spec-Befund 1,
              docs/superpowers/specs/2026-09-04-layout-14-zoll.md). minmax(0, Nfr)
              erlaubt beiden Spalten, tatsaechlich bis auf 0 zu schrumpfen, bevor
              der Browser ueberhaupt in horizontalen Overflow geht.
            */}
            <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,3fr)_minmax(0,1fr)] gap-6 items-stretch">
                {/* Main Content (Left/Center - 3/4 width) */}
                <div className="flex flex-col h-full min-h-[500px]">
                    <Card className="p-6 flex flex-col h-full">
                        {mainContent}
                    </Card>
                </div>

                {/* Side Content (Right - 1/4 width) */}
                <div className="space-y-6 flex flex-col h-full">
                    <Card className="p-6 h-full">
                        {sideContent}
                    </Card>
                </div>
            </div>
        </div>
    );
};
