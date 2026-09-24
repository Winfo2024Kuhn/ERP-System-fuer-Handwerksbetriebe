import type { Einheit } from './types';

const bezeichnungen: Record<Einheit, string> = {
  STUECK: 'Stück', METER: 'Meter', KILOGRAMM: 'Kilogramm', TONNE: 'Tonne', QUADRATMETER: 'Quadratmeter',
};
export const einheitenAnzeige = (einheit: Einheit | string | null | undefined): string =>
  einheit ? bezeichnungen[einheit as Einheit] ?? einheit : '';
