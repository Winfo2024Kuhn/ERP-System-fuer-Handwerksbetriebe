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
            <div className="grid grid-cols-1 xl:grid-cols-[3fr_1fr] gap-6 items-stretch">
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
