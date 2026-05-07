-- Konfigurierbarer Google-Bewertungs-Link je Betrieb.
-- Ersetzt den fest hartcodierten Link im EmailTemplateController, sodass jeder
-- Handwerksbetrieb seine eigene Bewertungs-URL pflegen kann (z. B. Google,
-- ProvenExpert, Trustpilot). Wird in E-Mail-Vorlagen ueber den Platzhalter
-- {{REVIEW_LINK}} eingesetzt.

ALTER TABLE firmeninformation
    ADD COLUMN google_bewertungs_link VARCHAR(500) NULL;
