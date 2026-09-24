import type { PunchoutForm } from '../types/ids';

/**
 * Baut eine versteckte Form mit den Punchout-Feldern und submittet sie
 * in ein neues Tab. Wir verwenden form.submit() — keine fetch()-Variante,
 * weil der Lieferanten-Shop ein klassisches Form-POST erwartet und auch
 * die Browser-Session anschließend für den Cart-Return offen halten muss.
 */
export function submitPunchoutForm(form: PunchoutForm) {
    const f = document.createElement('form');
    f.method = 'POST';
    f.action = form.action;
    f.target = '_blank';
    // rel="noopener noreferrer" verhindert (a) dass der Lieferanten-Shop ueber
    // window.opener Zugriff auf unseren ERP-Tab bekommt (Tabnabbing-Schutz) und
    // (b) dass interne ERP-URLs ueber den Referer-Header an den Shop leaken.
    f.setAttribute('rel', 'noopener noreferrer');
    f.style.display = 'none';
    f.acceptCharset = 'UTF-8';
    // IDS-Connect verlangt multipart/form-data.
    f.enctype = form.enctype || 'multipart/form-data';

    Object.entries(form.fields).forEach(([key, value]) => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = key;
        input.value = value ?? '';
        f.appendChild(input);
    });

    document.body.appendChild(f);
    f.submit();
    // Form sofort entfernen — der Browser hat die Werte schon zur Übertragung
    // serialisiert; wir wollen das versteckte DOM-Element nicht im Tree lassen.
    setTimeout(() => f.remove(), 100);
}
