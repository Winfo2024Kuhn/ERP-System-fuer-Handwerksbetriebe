import { SaxesParser } from 'saxes';

import type { IdsCart, IdsItem } from '../src/types/ids';
export type { IdsCart, IdsItem } from '../src/types/ids';

/** IDS 2.x XML, including default namespaces. No DTDs, external entities or HTML. */
export function parseIdsCart(xml: string): IdsCart {
  if (!xml || Buffer.byteLength(xml) > 2 * 1024 * 1024) throw new Error('Warenkorb fehlt oder ist zu groß.');
  const parser = new SaxesParser({ xmlns: true });
  const path: string[] = [];
  let text = '';
  let current: Record<string, string> | null = null;
  let flag = '';
  let reference = '';
  const info: Record<string, string> = {};
  const items: IdsItem[] = [];
  const decimal = (value: string, label: string, allowZero = false) => {
    if (!/^\d+(?:\.\d+)?$/.test(value)) throw new Error(`${label} ist ungültig.`);
    const n = Number(value);
    if (!Number.isFinite(n) || (allowZero ? n < 0 : n <= 0)) throw new Error(`${label} ist ungültig.`);
    return n;
  };
  parser.on('doctype', () => { throw new Error('DTD ist nicht erlaubt.'); });
  parser.on('opentag', tag => {
    path.push(tag.local); text = '';
    if (path.length > 20 || (path.length === 1 && tag.local !== 'Warenkorb')) throw new Error('Ungültiger IDS-Warenkorb.');
    if (path.join('/') === 'Warenkorb/Order/OrderItem') current = {};
  });
  parser.on('text', value => { text += value; });
  parser.on('cdata', value => { text += value; });
  parser.on('closetag', () => {
    const name = path.at(-1)!;
    const location = path.join('/');
    if (location === 'Warenkorb/WarenkorbInfo/RueckgabeKZ') flag = text.trim();
    if (location === 'Warenkorb/Order/OrderInfo/OrderConfNo') reference = text.trim();
    if (path.length === 4 && path[2] === 'OrderInfo') info[name] = text.trim();
    if (current && path.length === 5 && path[3] === 'RefItems') current[name] = text.trim();
    if (current && path.length === 4 && path[2] === 'OrderItem') current[name] = text.trim();
    if (location === 'Warenkorb/Order/OrderItem' && current) {
      if (!current.ArtNo || current.ArtNo.length > 100 || !current.QU || current.QU.length > 20) throw new Error('Artikelnummer oder Einheit fehlt oder ist zu lang.');
      const title = current.Kurztext || current.Langtext || current.ArtNo;
      if (title.length > 500 || items.length >= 500) throw new Error('Warenkorb überschreitet die Größenbegrenzung.');
      if (Object.values(current).some(value => value.length > 20000)) throw new Error('Artikeltext ist zu lang.');
      items.push({ description: current.Langtext || '', ean: current.EAN || '', unitCode: current.QU,
        vat: current.VAT ? decimal(current.VAT, 'Mehrwertsteuer', true) : null,
        customerReference: current.Customer || '', supplierReference: current.Supplier || '',
        hint: current.Hinweis || current.Fehlertext || '', article: current.ArtNo, name: title, quantity: decimal(current.Qty, 'Menge'),
        unit: ({ PCE: 'Stück', MTR: 'm', KGM: 'kg', LTR: 'l' } as Record<string, string>)[current.QU] ?? current.QU,
        netPrice: current.NetPrice ? decimal(current.NetPrice, 'Preis', true) : null,
        priceBasis: current.PriceBasis ? decimal(current.PriceBasis, 'Preisbasis') : 1 });
      current = null;
    }
    path.pop(); text = '';
  });
  parser.write(xml).close();
  if (!items.length || !['Warenkorbrückgabe', 'Warenkorbrückgabe mit Bestellung'].includes(flag)) throw new Error('Positionen oder Rückgabekennzeichen fehlen.');
  return { ordered: flag === 'Warenkorbrückgabe mit Bestellung', reference, items, currency: info.Cur || '', offerNumber: info.OfferNo || '',
    deliveryDate: info.DeliveryDate || '', shipment: info.ModeOfShipment === 'Abholung' ? 'Abholung' : 'Lieferung' };
}

const escapeXml = (value: string | number) => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[char]!);
const element = (name: string, value: string | number | null | undefined) => value == null || value === '' ? '' : `<${name}>${escapeXml(value)}</${name}>`;

/** ITEK Warenkorb_senden 2.5: WKS opens the shop, never submits a binding order. */
export function serializeIdsCart(cart: IdsCart, number: string): string {
  if (cart.ordered || !cart.items.length) throw new Error('Dieser Warenkorb kann nicht erneut bestellt werden.');
  const date = new Date().toISOString();
  return `<?xml version="1.0" encoding="UTF-8"?><Warenkorb xmlns="http://www.itek.de/Shop-Anbindung/Warenkorb/"><WarenkorbInfo>${element('Date', date.slice(0, 10))}${element('Time', date.slice(11, 19))}<Version>2.5</Version></WarenkorbInfo><Order><OrderInfo>${element('OfferNo', cart.offerNumber)}${element('PartNo', number)}${element('ModeOfShipment', cart.shipment || 'Lieferung')}${element('Cur', cart.currency)}</OrderInfo>${cart.items.map((item, index) => `<OrderItem><RefItems>${element('Customer', item.customerReference || String(index + 1))}${element('Supplier', item.supplierReference)}</RefItems>${element('EAN', item.ean)}${element('ArtNo', item.article)}${element('Qty', item.quantity)}${element('QU', item.unitCode || item.unit)}${element('Kurztext', item.name.slice(0, 100))}${element('Langtext', item.description)}${element('NetPrice', item.netPrice)}${element('PriceBasis', item.priceBasis)}${element('VAT', item.vat)}</OrderItem>`).join('')}</Order></Warenkorb>`;
}
