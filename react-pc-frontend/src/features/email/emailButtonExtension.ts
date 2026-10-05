import { Node } from '@tiptap/core';
import { ReactNodeViewRenderer } from '@tiptap/react';
import { EmailButtonNodeView } from './EmailButtonNodeView';
import { EMAIL_BUTTON_FARBE, bereinigeButtonAdresse } from './emailButton';

export interface EmailButtonAttribute {
  text: string;
  href: string;
}

export interface EmailButtonOptions {
  /** Wird aufgerufen, wenn der Nutzer im Editor auf "Ändern" klickt. */
  onBearbeiten: (attribute: EmailButtonAttribute, pos: number) => void;
}

/**
 * Klickbarer Button in E-Mail-Vorlagen (z. B. "Jetzt Bewertung abgeben").
 *
 * Gespeichert wird er als Tabelle mit eingefaerbter Zelle statt als
 * gestyltes <a>: Outlook ignoriert Hintergrundfarben auf Links, auf
 * <td bgcolor> dagegen nicht. Gleiches Muster wie der Annahme-Button in
 * DokumentFreigabeService.buildFreigabeBlockHtml. `not-prose` haelt die
 * Tabellen-Styles der Vorschau fern.
 */
export const EmailButton = Node.create<EmailButtonOptions>({
  name: 'emailButton',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addOptions() {
    return {
      onBearbeiten: () => {},
    };
  },

  addAttributes() {
    return {
      text: {
        default: '',
        parseHTML: (element: HTMLElement) => element.querySelector('a')?.textContent?.trim() ?? '',
        renderHTML: () => ({}),
      },
      href: {
        default: '',
        parseHTML: (element: HTMLElement) =>
          bereinigeButtonAdresse(element.querySelector('a')?.getAttribute('href')),
        renderHTML: () => ({}),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'table[data-email-button]' }];
  },

  renderHTML({ node }) {
    const href = bereinigeButtonAdresse(node.attrs.href as string);
    return [
      'table',
      {
        'data-email-button': '',
        class: 'not-prose',
        role: 'presentation',
        cellpadding: '0',
        cellspacing: '0',
        border: '0',
        style: 'border-collapse:separate;margin:16px 0;',
      },
      [
        'tbody',
        [
          'tr',
          [
            'td',
            { bgcolor: EMAIL_BUTTON_FARBE, style: 'border-radius:6px;' },
            [
              'a',
              {
                href,
                target: '_blank',
                rel: 'noopener noreferrer',
                style:
                  'display:inline-block;padding:13px 26px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:6px;',
              },
              node.attrs.text as string,
            ],
          ],
        ],
      ],
    ];
  },

  addNodeView() {
    return ReactNodeViewRenderer(EmailButtonNodeView);
  },
});
