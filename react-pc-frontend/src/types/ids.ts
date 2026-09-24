export interface IdsItem {
  article: string; name: string; quantity: number; unit: string;
  netPrice: number | null; priceBasis: number;
  description?: string; ean?: string; vat?: number | null; unitCode?: string;
  customerReference?: string; supplierReference?: string; hint?: string;
}
export interface IdsCart {
  ordered: boolean; reference: string; items: IdsItem[];
  currency?: string; offerNumber?: string; deliveryDate?: string; shipment?: string;
}
/** Projekt, an dem ein Shop-Warenkorb geparkt ist (aus dem Projektbedarf gestartet). */
export interface IdsProjektZuordnung { projektId: number; projektName?: string }
export interface IdsDraft extends IdsCart { id: string; number: string; updatedAt: string; projektId?: number; projektName?: string }
export interface PunchoutForm { action: string; enctype?: string; fields: Record<string, string> }
