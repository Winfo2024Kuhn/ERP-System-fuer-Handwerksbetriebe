-- =============================================================================
-- PostgreSQL-Basis-Schema fuer NEUINSTALLATIONEN - nicht von Hand bearbeiten!
-- Erzeugt mit scripts/basis-schema/postgres_erzeugen.sh (Erklaerung dort).
-- Entspricht der MySQL-Basis (db/basis): Schema, Stammdaten, Indizes.
-- Keine Kunden- oder Personendaten.
-- Wird nur auf einer LEEREN Datenbank ausgefuehrt (FlywayStartSetupConfig).
-- =============================================================================
--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: abteilung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abteilung (
    darf_freigabe_annahme_pushen boolean DEFAULT true NOT NULL,
    darf_monat_abschliessen boolean DEFAULT false NOT NULL,
    darf_rechnungen_genehmigen boolean NOT NULL,
    darf_rechnungen_sehen boolean NOT NULL,
    darf_telefon_sehen boolean DEFAULT false NOT NULL,
    darf_webseiten_anfragen_pushen boolean DEFAULT true NOT NULL,
    id bigint NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: abteilung_dokument_berechtigung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abteilung_dokument_berechtigung (
    darf_scannen boolean NOT NULL,
    darf_sehen boolean NOT NULL,
    abteilung_id bigint NOT NULL,
    id bigint NOT NULL,
    dokument_typ character varying(50) NOT NULL
);


--
-- Name: abteilung_dokument_berechtigung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.abteilung_dokument_berechtigung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: abteilung_dokument_berechtigung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.abteilung_dokument_berechtigung_id_seq OWNED BY public.abteilung_dokument_berechtigung.id;


--
-- Name: abteilung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.abteilung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: abteilung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.abteilung_id_seq OWNED BY public.abteilung.id;


--
-- Name: abwesenheit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abwesenheit (
    datum date NOT NULL,
    stunden numeric(10,2) NOT NULL,
    id bigint NOT NULL,
    langzeitkrankmeldung_id bigint,
    langzeitkrankmeldung_phase_id bigint,
    mitarbeiter_id bigint NOT NULL,
    urlaubsantrag_id bigint,
    typ character varying(20) NOT NULL,
    notiz character varying(500)
);


--
-- Name: abwesenheit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.abwesenheit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: abwesenheit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.abwesenheit_id_seq OWNED BY public.abwesenheit.id;


--
-- Name: aenderungsgrund_katalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aenderungsgrund_katalog (
    erfordert_freitext boolean,
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    bezeichnung character varying(255) NOT NULL
);


--
-- Name: aenderungsgrund_katalog_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aenderungsgrund_katalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aenderungsgrund_katalog_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.aenderungsgrund_katalog_id_seq OWNED BY public.aenderungsgrund_katalog.id;


--
-- Name: anfrage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage (
    abgeschlossen boolean NOT NULL,
    anlegedatum date,
    betrag numeric(38,2),
    email_versand_datum date,
    created_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    id bigint NOT NULL,
    kunde_id bigint,
    projekt_id bigint,
    version bigint DEFAULT 0,
    kurzbeschreibung character varying(1000),
    bauvorhaben character varying(255),
    bild_url character varying(255),
    projekt_ort character varying(255),
    projekt_plz character varying(255),
    projekt_strasse character varying(255)
);


--
-- Name: anfrage_dokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage_dokument (
    email_versand_datum date,
    upload_datum date,
    anfrage_id bigint,
    dateigroesse bigint,
    id bigint NOT NULL,
    dateityp character varying(255),
    dokument_gruppe character varying(255) NOT NULL,
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255) NOT NULL
);


--
-- Name: anfrage_dokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.anfrage_dokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anfrage_dokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.anfrage_dokument_id_seq OWNED BY public.anfrage_dokument.id;


--
-- Name: anfrage_geschaeftsdokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage_geschaeftsdokument (
    brutto_betrag numeric(38,2),
    id bigint NOT NULL,
    dokumentid character varying(255) NOT NULL,
    geschaeftsdokumentart character varying(255) NOT NULL
);


--
-- Name: anfrage_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.anfrage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anfrage_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.anfrage_id_seq OWNED BY public.anfrage.id;


--
-- Name: anfrage_kunden_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage_kunden_emails (
    anfrage_id bigint NOT NULL,
    email character varying(255)
);


--
-- Name: anfrage_notiz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage_notiz (
    mobile_sichtbar boolean NOT NULL,
    nur_fuer_ersteller boolean NOT NULL,
    anfrage_id bigint NOT NULL,
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    notiz character varying(4000) NOT NULL
);


--
-- Name: anfrage_notiz_bild; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.anfrage_notiz_bild (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    notiz_id bigint NOT NULL,
    dateityp character varying(255),
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255)
);


--
-- Name: anfrage_notiz_bild_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.anfrage_notiz_bild_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anfrage_notiz_bild_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.anfrage_notiz_bild_id_seq OWNED BY public.anfrage_notiz_bild.id;


--
-- Name: anfrage_notiz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.anfrage_notiz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anfrage_notiz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.anfrage_notiz_id_seq OWNED BY public.anfrage_notiz.id;


--
-- Name: arbeitsgang; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arbeitsgang (
    abteilung_id bigint NOT NULL,
    id bigint NOT NULL,
    version bigint DEFAULT 0,
    beschreibung character varying(255) NOT NULL
);


--
-- Name: arbeitsgang_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.arbeitsgang_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: arbeitsgang_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.arbeitsgang_id_seq OWNED BY public.arbeitsgang.id;


--
-- Name: arbeitsgang_stundensatz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arbeitsgang_stundensatz (
    jahr integer NOT NULL,
    satz numeric(10,2) NOT NULL,
    arbeitsgang_id bigint NOT NULL,
    id bigint NOT NULL
);


--
-- Name: arbeitsgang_stundensatz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.arbeitsgang_stundensatz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: arbeitsgang_stundensatz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.arbeitsgang_stundensatz_id_seq OWNED BY public.arbeitsgang_stundensatz.id;


--
-- Name: arbeitszeitart; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arbeitszeitart (
    aktiv boolean NOT NULL,
    sortierung integer NOT NULL,
    stundensatz numeric(10,2) NOT NULL,
    id bigint NOT NULL,
    bezeichnung character varying(100) NOT NULL,
    beschreibung text
);


--
-- Name: arbeitszeitart_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.arbeitszeitart_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: arbeitszeitart_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.arbeitszeitart_id_seq OWNED BY public.arbeitszeitart.id;


--
-- Name: artikel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artikel (
    kategorie_id integer,
    pulverbeschichtungsgeeignet boolean,
    system_stammdaten boolean NOT NULL,
    verkaufsaufschlag_prozent numeric(5,2),
    verzinkungsgeeignet boolean,
    id bigint NOT NULL,
    verpackungseinheit bigint,
    version bigint DEFAULT 0,
    werkstoff_id bigint,
    artikelnummer character varying(64),
    massnorm character varying(64),
    werkstoffnorm character varying(64),
    beschreibung text,
    fertigungszustand character varying(255),
    herstellverfahren character varying(255),
    hicad_name character varying(255),
    kurzbeschreibung character varying(255),
    preiseinheit character varying(255),
    produktlinie character varying(255),
    produktname character varying(255),
    produkttext character varying(255),
    profilform character varying(255),
    suchtext text,
    verrechnungseinheit character varying(255)
);


--
-- Name: artikel_dokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artikel_dokument (
    sortierung integer,
    artikel_id bigint NOT NULL,
    dateigroesse_bytes bigint,
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint,
    beschreibung character varying(1000),
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255) NOT NULL,
    typ character varying(255) NOT NULL
);


--
-- Name: artikel_dokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artikel_dokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artikel_dokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artikel_dokument_id_seq OWNED BY public.artikel_dokument.id;


--
-- Name: artikel_hilfsstoffe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artikel_hilfsstoffe (
    id bigint NOT NULL,
    masse_pro_meter bigint
);


--
-- Name: artikel_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artikel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artikel_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artikel_id_seq OWNED BY public.artikel.id;


--
-- Name: artikel_in_projekt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artikel_in_projekt (
    aus_lager boolean DEFAULT false NOT NULL,
    bestellt boolean NOT NULL,
    bestellt_am date,
    hinzugefuegt_am date NOT NULL,
    kilogramm numeric(19,2),
    meter numeric(19,2),
    preis_pro_stueck numeric(19,4),
    stueckzahl integer,
    artikel_id bigint NOT NULL,
    id bigint NOT NULL,
    lieferant_id bigint,
    lieferanten_artikel_preis_id bigint,
    projekt_id bigint NOT NULL,
    anschnitt_winkel_links character varying(255),
    anschnitt_winkel_rechts character varying(255),
    kommentar character varying(255),
    schnitt_form character varying(255)
);


--
-- Name: artikel_in_projekt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.artikel_in_projekt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: artikel_in_projekt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.artikel_in_projekt_id_seq OWNED BY public.artikel_in_projekt.id;


--
-- Name: artikel_werkstoffe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artikel_werkstoffe (
    breite numeric(10,2),
    durchmesser numeric(10,2),
    flanschdicke numeric(10,2),
    geschliffen boolean DEFAULT false NOT NULL,
    hoehe numeric(10,2),
    mantelflaeche numeric(12,4),
    masse_pro_meter numeric(12,4),
    masse_pro_qm numeric(10,4),
    querschnittsflaeche numeric(10,3),
    standardlaenge_mm integer,
    stegdicke numeric(10,2),
    wandstaerke numeric(10,2),
    id bigint NOT NULL
);


--
-- Name: audit_chain_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_chain_state (
    id integer NOT NULL,
    last_chain_index bigint DEFAULT '-1'::integer NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    last_entry_hash character(64),
    CONSTRAINT chk_audit_chain_state_singleton CHECK ((id = 1))
);


--
-- Name: ausgangs_geschaeftsdokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ausgangs_geschaeftsdokument (
    abschlags_nummer integer,
    betrag_brutto numeric(12,2),
    betrag_netto numeric(12,2),
    datum date NOT NULL,
    digital_angenommen boolean DEFAULT false NOT NULL,
    gebucht boolean NOT NULL,
    gebucht_am date,
    mwst_satz numeric(5,4),
    storniert boolean NOT NULL,
    storniert_am date,
    versand_datum date,
    zahlungsziel_tage integer,
    anfrage_id bigint,
    erstellt_am timestamp(6) without time zone NOT NULL,
    erstellt_von_id bigint,
    geaendert_am timestamp(6) without time zone,
    id bigint NOT NULL,
    kunde_id bigint,
    projekt_id bigint,
    version bigint DEFAULT 0,
    vorgaenger_id bigint,
    dokument_nummer character varying(20) NOT NULL,
    typ character varying(30) NOT NULL,
    betreff character varying(500),
    rechnungsadresse_override character varying(500),
    html_inhalt text,
    pdf_dateiname character varying(255),
    positionen_json text
);


--
-- Name: ausgangs_geschaeftsdokument_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ausgangs_geschaeftsdokument_audit (
    abschlags_nummer integer,
    betrag_brutto numeric(12,2),
    betrag_netto numeric(12,2),
    datum date,
    digital_angenommen boolean DEFAULT false NOT NULL,
    gebucht boolean DEFAULT false NOT NULL,
    gebucht_am date,
    mwst_satz numeric(5,4),
    storniert boolean DEFAULT false NOT NULL,
    storniert_am date,
    versand_datum date,
    anfrage_id bigint,
    chain_index bigint,
    dokument_id bigint NOT NULL,
    geaendert_am timestamp(6) without time zone NOT NULL,
    geaendert_von_id bigint,
    id bigint NOT NULL,
    kunde_id bigint,
    projekt_id bigint,
    vorgaenger_id bigint,
    aktion character varying(20) NOT NULL,
    dokument_nummer character varying(20) NOT NULL,
    typ character varying(30) NOT NULL,
    ip_adresse character varying(45),
    betreff character varying(500),
    aenderungsgrund text,
    entry_hash character(64),
    inhalt_hash character(64),
    previous_hash character(64)
);


--
-- Name: ausgangs_geschaeftsdokument_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ausgangs_geschaeftsdokument_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ausgangs_geschaeftsdokument_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ausgangs_geschaeftsdokument_audit_id_seq OWNED BY public.ausgangs_geschaeftsdokument_audit.id;


--
-- Name: ausgangs_geschaeftsdokument_counter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ausgangs_geschaeftsdokument_counter (
    id bigint NOT NULL,
    zaehler bigint NOT NULL,
    monat_key character varying(10) NOT NULL
);


--
-- Name: ausgangs_geschaeftsdokument_counter_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ausgangs_geschaeftsdokument_counter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ausgangs_geschaeftsdokument_counter_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ausgangs_geschaeftsdokument_counter_id_seq OWNED BY public.ausgangs_geschaeftsdokument_counter.id;


--
-- Name: ausgangs_geschaeftsdokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ausgangs_geschaeftsdokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ausgangs_geschaeftsdokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ausgangs_geschaeftsdokument_id_seq OWNED BY public.ausgangs_geschaeftsdokument.id;


--
-- Name: beleg; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.beleg (
    beleg_datum date,
    betrag_brutto numeric(15,2),
    betrag_firma_brutto numeric(15,2),
    betrag_firma_mwst numeric(15,2),
    betrag_firma_netto numeric(15,2),
    betrag_netto numeric(15,2),
    festgeschrieben boolean DEFAULT false NOT NULL,
    ist_umbuchung boolean DEFAULT false NOT NULL,
    ki_belegdatum date,
    ki_betrag_brutto numeric(15,2),
    ki_confidence numeric(3,2),
    ki_kostenkonto_confidence numeric(3,2),
    mwst_satz numeric(5,2),
    ausgangsrechnung_id bigint,
    festgeschrieben_am timestamp(6) without time zone,
    festgeschrieben_von_id bigint,
    id bigint NOT NULL,
    ki_vorgeschlagener_kostenstelle_id bigint,
    ki_vorgeschlagener_sachkonto_id bigint,
    kostenstelle_id bigint,
    laufende_nummer bigint,
    lieferant_id bigint,
    monatsabschluss_id bigint,
    sachkonto_id bigint,
    storniert_am timestamp(6) without time zone,
    storniert_durch_beleg_id bigint,
    storno_fuer_beleg_id bigint,
    upload_datum timestamp(6) without time zone NOT NULL,
    uploaded_by_id bigint,
    validiert_am timestamp(6) without time zone,
    validiert_von_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    aufteilungs_modus character varying(20) DEFAULT 'VOLLSTAENDIG'::character varying NOT NULL,
    ki_analyse_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    quelle character varying(20) DEFAULT 'SCAN'::character varying NOT NULL,
    status character varying(20) DEFAULT 'NEU'::character varying NOT NULL,
    dokument_typ character varying(30),
    beleg_kategorie character varying(40) DEFAULT 'UNZUGEORDNET'::character varying NOT NULL,
    ki_zahlungsart character varying(40),
    zahlungsart character varying(40),
    beleg_nummer character varying(100),
    gegenpartei character varying(120),
    mime_type character varying(120),
    beschreibung character varying(500),
    ki_kostenkonto_begruendung character varying(500),
    storno_grund character varying(500),
    ki_fehler_text character varying(1000),
    notiz character varying(1000),
    datei_hash character(64),
    gespeicherter_dateiname character varying(255),
    ki_extraktion_json text,
    ki_kostenkonto_hinweis character varying(255),
    ki_vorgeschlagener_lieferant character varying(255),
    original_dateiname character varying(255),
    CONSTRAINT chk_beleg_datei_oder_umbuchung CHECK (((ist_umbuchung = true) OR (gespeicherter_dateiname IS NOT NULL)))
);


--
-- Name: beleg_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.beleg_audit (
    beleg_datum date,
    betrag_brutto numeric(15,2),
    betrag_firma_brutto numeric(15,2),
    betrag_firma_mwst numeric(15,2),
    betrag_firma_netto numeric(15,2),
    betrag_netto numeric(15,2),
    festgeschrieben boolean DEFAULT false NOT NULL,
    ist_umbuchung boolean DEFAULT false NOT NULL,
    mwst_satz numeric(5,2),
    beleg_id bigint,
    bezug_id bigint,
    chain_index bigint,
    geaendert_am timestamp(6) without time zone NOT NULL,
    geaendert_von_id bigint,
    id bigint NOT NULL,
    kostenstelle_id bigint,
    laufende_nummer bigint,
    lieferant_id bigint,
    sachkonto_id bigint,
    storniert_durch_beleg_id bigint,
    storno_fuer_beleg_id bigint,
    aufteilungs_modus character varying(20),
    beleg_status character varying(20),
    sachkonto_nummer character varying(20),
    aktion character varying(30) NOT NULL,
    bezug_typ character varying(30),
    beleg_kategorie character varying(40),
    zahlungsart character varying(40),
    ip_adresse character varying(45),
    beleg_nummer character varying(100),
    beschreibung character varying(500),
    aenderungsgrund text,
    datei_hash character(64),
    entry_hash character(64),
    gespeicherter_dateiname character varying(255),
    previous_hash character(64),
    zusatz text
);


--
-- Name: beleg_audit_chain_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.beleg_audit_chain_state (
    id integer NOT NULL,
    last_chain_index bigint DEFAULT '-1'::integer NOT NULL,
    last_laufende_nummer bigint DEFAULT 0 NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    last_entry_hash character(64),
    CONSTRAINT chk_beleg_audit_chain_state_singleton CHECK ((id = 1))
);


--
-- Name: beleg_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.beleg_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: beleg_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.beleg_audit_id_seq OWNED BY public.beleg_audit.id;


--
-- Name: beleg_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.beleg_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: beleg_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.beleg_id_seq OWNED BY public.beleg.id;


--
-- Name: beleg_kostenstellen_anteil; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.beleg_kostenstellen_anteil (
    absoluter_betrag numeric(15,2),
    berechneter_betrag numeric(15,2),
    prozent integer,
    streckung_jahre integer DEFAULT 1 NOT NULL,
    streckung_start_jahr integer,
    beleg_id bigint NOT NULL,
    id bigint NOT NULL,
    kostenstelle_id bigint NOT NULL,
    zugeordnet_am timestamp(6) without time zone,
    zugeordnet_von_user_id bigint,
    beschreibung character varying(255)
);


--
-- Name: beleg_kostenstellen_anteil_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.beleg_kostenstellen_anteil_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: beleg_kostenstellen_anteil_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.beleg_kostenstellen_anteil_id_seq OWNED BY public.beleg_kostenstellen_anteil.id;


--
-- Name: beleg_position; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.beleg_position (
    betrag_brutto numeric(15,2),
    betrag_netto numeric(15,2),
    einzelpreis numeric(15,4),
    ist_fuer_firma boolean DEFAULT false NOT NULL,
    menge numeric(15,3),
    mwst_satz numeric(5,2),
    sortierung integer DEFAULT 0 NOT NULL,
    beleg_id bigint NOT NULL,
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    einheit character varying(20),
    beschreibung character varying(500) NOT NULL
);


--
-- Name: beleg_position_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.beleg_position_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: beleg_position_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.beleg_position_id_seq OWNED BY public.beleg_position.id;


--
-- Name: bwa_position; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bwa_position (
    betrag_kumuliert numeric(14,2),
    betrag_monat numeric(14,2) NOT NULL,
    differenz numeric(14,2),
    in_rechnungen_gefunden boolean NOT NULL,
    manuell_korrigiert boolean NOT NULL,
    rechnungssumme numeric(14,2),
    bwa_upload_id bigint NOT NULL,
    id bigint NOT NULL,
    kostenstelle_id bigint,
    kontonummer character varying(20),
    kategorie character varying(50),
    notiz character varying(500),
    bezeichnung character varying(255) NOT NULL
);


--
-- Name: bwa_position_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bwa_position_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bwa_position_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bwa_position_id_seq OWNED BY public.bwa_position.id;


--
-- Name: bwa_upload; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bwa_upload (
    ai_confidence double precision,
    analysiert boolean NOT NULL,
    freigegeben boolean NOT NULL,
    gesamt_gemeinkosten numeric(14,2),
    jahr integer NOT NULL,
    kosten_aus_bwa numeric(14,2),
    kosten_aus_rechnungen numeric(14,2),
    monat integer,
    analyse_datum timestamp(6) without time zone,
    email_id bigint,
    freigegeben_am timestamp(6) without time zone,
    freigegeben_von_id bigint,
    id bigint NOT NULL,
    steuerberater_id bigint,
    upload_datum timestamp(6) without time zone NOT NULL,
    ai_raw_json text,
    gespeicherter_dateiname character varying(255),
    original_dateiname character varying(255),
    typ character varying(255) NOT NULL
);


--
-- Name: bwa_upload_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bwa_upload_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bwa_upload_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bwa_upload_id_seq OWNED BY public.bwa_upload.id;


--
-- Name: datensatz_lock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.datensatz_lock (
    acquired_at timestamp(6) without time zone NOT NULL,
    entitaet_id bigint NOT NULL,
    id bigint NOT NULL,
    last_heartbeat_at timestamp(6) without time zone NOT NULL,
    user_id bigint NOT NULL,
    entitaet_typ character varying(32) NOT NULL,
    user_display_name character varying(255) NOT NULL
);


--
-- Name: datensatz_lock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.datensatz_lock_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: datensatz_lock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.datensatz_lock_id_seq OWNED BY public.datensatz_lock.id;


--
-- Name: datev_konfiguration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.datev_konfiguration (
    mandanten_nr character varying(5) DEFAULT ''::character varying NOT NULL,
    berater_nr character varying(7) DEFAULT ''::character varying NOT NULL,
    aenderungszaehler bigint DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    ziel character varying(10) DEFAULT 'LODAS'::character varying NOT NULL,
    zuordnungen_json text NOT NULL
);


--
-- Name: datev_personalnummer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.datev_personalnummer (
    normalisiert character varying(5) NOT NULL,
    personalnummer character varying(5) NOT NULL,
    mitarbeiter_id bigint NOT NULL
);


--
-- Name: dokument_freigabe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dokument_freigabe (
    akzeptierter_betrag numeric(12,2),
    basis_netto numeric(12,2),
    dokument_betrag numeric(12,2),
    mwst_satz numeric(5,4),
    ablauf_datum timestamp(6) without time zone NOT NULL,
    akzeptiert_am timestamp(6) without time zone,
    erstellt_am timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    id bigint NOT NULL,
    quell_dokument_id bigint NOT NULL,
    quell_typ character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    uuid character varying(36) NOT NULL,
    akzeptiert_ip character varying(45),
    dokument_art character varying(50) NOT NULL,
    unterzeichner_nachname character varying(80),
    unterzeichner_vorname character varying(80),
    dokument_nummer character varying(100) NOT NULL,
    hash_acceptance character varying(128),
    hash_original character varying(128) NOT NULL,
    unterzeichner_name character varying(160),
    akzeptiert_user_agent character varying(500),
    bauvorhaben character varying(500),
    akzeptiert_email character varying(255),
    akzeptierte_alternativen text,
    dokument_datei character varying(255),
    kunde_email character varying(255),
    kunde_name character varying(255),
    positionen_snapshot text
);


--
-- Name: dokument_freigabe_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dokument_freigabe_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dokument_freigabe_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dokument_freigabe_id_seq OWNED BY public.dokument_freigabe.id;


--
-- Name: dokumentnummer_counter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dokumentnummer_counter (
    counter bigint NOT NULL,
    id bigint NOT NULL,
    month_key character varying(10) NOT NULL
);


--
-- Name: dokumentnummer_counter_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dokumentnummer_counter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dokumentnummer_counter_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dokumentnummer_counter_id_seq OWNED BY public.dokumentnummer_counter.id;


--
-- Name: email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email (
    bayes_score double precision,
    inquiry_score integer,
    is_newsletter boolean NOT NULL,
    is_potential_inquiry boolean NOT NULL,
    is_read boolean NOT NULL,
    is_spam boolean NOT NULL,
    is_starred boolean DEFAULT false NOT NULL,
    spam_score integer,
    anfrage_id bigint,
    deleted_at timestamp(6) without time zone,
    first_viewed_at timestamp(6) without time zone,
    id bigint NOT NULL,
    imap_uid bigint,
    lieferant_id bigint,
    parent_email_id bigint,
    processed_at timestamp(6) without time zone,
    projekt_id bigint,
    sent_at timestamp(6) without time zone,
    steuerberater_id bigint,
    zustell_geprueft_am timestamp(6) without time zone,
    user_spam_verdict character varying(20),
    zustell_fehler character varying(500),
    message_id character varying(512) NOT NULL,
    cc character varying(1000),
    recipient character varying(1000),
    subject character varying(1000),
    authentication_results text,
    body text,
    direction character varying(255) NOT NULL,
    error_message text,
    from_address character varying(255),
    html_body text,
    imap_folder character varying(255),
    processing_status character varying(255) NOT NULL,
    raw_body text,
    reply_to_address character varying(255),
    sender_domain character varying(255),
    zuordnung_typ character varying(255) NOT NULL,
    zustell_status character varying(255) DEFAULT 'OFFEN'::character varying NOT NULL
);


--
-- Name: email_absender; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_absender (
    aktiv boolean DEFAULT true NOT NULL,
    sortierung integer DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    anzeigename character varying(255),
    email_adresse character varying(255) NOT NULL
);


--
-- Name: email_absender_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_absender_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_absender_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_absender_id_seq OWNED BY public.email_absender.id;


--
-- Name: email_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_attachment (
    ai_processed boolean,
    inline_attachment boolean,
    ai_processed_at timestamp(6) without time zone,
    email_id bigint NOT NULL,
    id bigint NOT NULL,
    lieferant_dokument_id bigint,
    size_bytes bigint,
    original_filename character varying(500),
    stored_filename character varying(500),
    content_id character varying(255),
    mime_type character varying(255)
);


--
-- Name: email_attachment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_attachment_id_seq OWNED BY public.email_attachment.id;


--
-- Name: email_blacklist_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_blacklist_entry (
    blocked_at timestamp(6) without time zone,
    id bigint NOT NULL,
    blocked_by character varying(255),
    email_address character varying(255) NOT NULL
);


--
-- Name: email_blacklist_entry_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_blacklist_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_blacklist_entry_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_blacklist_entry_id_seq OWNED BY public.email_blacklist_entry.id;


--
-- Name: email_draft; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_draft (
    geschaeftsdokument boolean DEFAULT false NOT NULL,
    anfrage_id bigint,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    projekt_id bigint,
    reply_email_id bigint,
    updated_at timestamp(6) without time zone,
    body text,
    cc text,
    from_address character varying(255),
    recipient text,
    subject text
);


--
-- Name: email_draft_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_draft_attachment (
    draft_id bigint NOT NULL,
    id bigint NOT NULL,
    size bigint NOT NULL,
    content_type character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    data bytea NOT NULL
);


--
-- Name: email_draft_attachment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_draft_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_draft_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_draft_attachment_id_seq OWNED BY public.email_draft_attachment.id;


--
-- Name: email_draft_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_draft_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_draft_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_draft_id_seq OWNED BY public.email_draft.id;


--
-- Name: email_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_id_seq OWNED BY public.email.id;


--
-- Name: email_signature; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_signature (
    is_system_default boolean DEFAULT false NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    name character varying(200) NOT NULL,
    html text NOT NULL
);


--
-- Name: email_signature_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_signature_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_signature_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_signature_id_seq OWNED BY public.email_signature.id;


--
-- Name: email_signature_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_signature_image (
    sort_order integer,
    id bigint NOT NULL,
    signature_id bigint NOT NULL,
    size_bytes bigint NOT NULL,
    cid character varying(120) NOT NULL,
    content_type character varying(255) NOT NULL,
    original_filename character varying(255) NOT NULL,
    stored_filename character varying(255) NOT NULL
);


--
-- Name: email_signature_image_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_signature_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_signature_image_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_signature_image_id_seq OWNED BY public.email_signature_image.id;


--
-- Name: email_text_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_text_template (
    aktiv boolean DEFAULT true NOT NULL,
    created_at timestamp(6) with time zone,
    id bigint NOT NULL,
    updated_at timestamp(6) with time zone,
    kategorie character varying(20),
    dokument_typ character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    subject_template character varying(500) NOT NULL,
    html_body text NOT NULL
);


--
-- Name: email_text_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_text_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_text_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_text_template_id_seq OWNED BY public.email_text_template.id;


--
-- Name: entity_last_accessed; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.entity_last_accessed (
    entity_id bigint NOT NULL,
    user_id bigint NOT NULL,
    zugegriffen_am timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    entity_type character varying(64) NOT NULL
);


--
-- Name: feiertag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feiertag (
    datum date NOT NULL,
    halb_tag boolean NOT NULL,
    id bigint NOT NULL,
    bundesland character varying(10) NOT NULL,
    bezeichnung character varying(255) NOT NULL
);


--
-- Name: feiertag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.feiertag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: feiertag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.feiertag_id_seq OWNED BY public.feiertag.id;


--
-- Name: firma_kostenstelle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.firma_kostenstelle (
    aktiv boolean NOT NULL,
    ist_fixkosten boolean NOT NULL,
    ist_investition boolean NOT NULL,
    sortierung integer,
    id bigint NOT NULL,
    beschreibung character varying(500),
    name character varying(255) NOT NULL,
    typ character varying(255) NOT NULL
);


--
-- Name: firma_kostenstelle_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.firma_kostenstelle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: firma_kostenstelle_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.firma_kostenstelle_id_seq OWNED BY public.firma_kostenstelle.id;


--
-- Name: firmeninformation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.firmeninformation (
    bg_satz_override numeric(5,2),
    mahnverfahren_aktiv boolean DEFAULT false NOT NULL,
    mahnverfahren_neues_zahlungsziel_tage integer DEFAULT 7 NOT NULL,
    tage_bis_erste_mahnung integer DEFAULT 14 NOT NULL,
    tage_bis_zahlungserinnerung integer DEFAULT 7 NOT NULL,
    tage_bis_zweite_mahnung integer DEFAULT 21 NOT NULL,
    firmenfarbe character varying(7),
    gewerk_id bigint,
    id bigint NOT NULL,
    version bigint DEFAULT 0,
    google_bewertungs_link character varying(500),
    fusszeile_text character varying(1000),
    bank_name character varying(255),
    bic character varying(255),
    email character varying(255),
    fax character varying(255),
    firmenname character varying(255) NOT NULL,
    geschaeftsfuehrer character varying(255),
    handelsregister character varying(255),
    handelsregister_nummer character varying(255),
    iban character varying(255),
    logo_dateiname character varying(255),
    ort character varying(255),
    plz character varying(255),
    steuernummer character varying(255),
    strasse character varying(255),
    telefon character varying(255),
    ust_id_nr character varying(255),
    website character varying(255)
);


--
-- Name: formular_template_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.formular_template_assignment (
    id bigint NOT NULL,
    user_id bigint,
    dokumenttyp_enum character varying(30) NOT NULL,
    template_name character varying(150) NOT NULL
);


--
-- Name: formular_template_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.formular_template_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: formular_template_assignment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.formular_template_assignment_id_seq OWNED BY public.formular_template_assignment.id;


--
-- Name: formular_template_textbaustein_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.formular_template_textbaustein_default (
    sort_order integer DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    "position" character varying(8) NOT NULL,
    textbaustein_id bigint NOT NULL,
    dokumenttyp character varying(40) NOT NULL,
    template_name character varying(150) NOT NULL
);


--
-- Name: formular_template_textbaustein_default_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.formular_template_textbaustein_default_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: formular_template_textbaustein_default_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.formular_template_textbaustein_default_id_seq OWNED BY public.formular_template_textbaustein_default.id;


--
-- Name: frontend_user_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.frontend_user_profile (
    active boolean NOT NULL,
    default_signature_id bigint,
    email_absender_id bigint,
    id bigint NOT NULL,
    mitarbeiter_id bigint,
    short_code character varying(50),
    username character varying(120),
    display_name character varying(200) NOT NULL,
    password_hash character varying(255)
);


--
-- Name: frontend_user_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.frontend_user_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: frontend_user_profile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.frontend_user_profile_id_seq OWNED BY public.frontend_user_profile.id;


--
-- Name: frontend_user_profile_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.frontend_user_profile_role (
    frontend_user_profile_id bigint NOT NULL,
    role_name character varying(50) NOT NULL
);


--
-- Name: gewerk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gewerk (
    aktiv boolean DEFAULT true NOT NULL,
    bg_satz_prozent numeric(5,2) NOT NULL,
    id bigint NOT NULL,
    bemerkung character varying(500),
    bg_name character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: gewerk_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gewerk_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: gewerk_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.gewerk_id_seq OWNED BY public.gewerk.id;


--
-- Name: kalender_eintrag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kalender_eintrag (
    datum date NOT NULL,
    ende_zeit time(6) without time zone,
    ganztaegig boolean NOT NULL,
    start_zeit time(6) without time zone,
    aktualisiert_am timestamp(6) without time zone,
    anfrage_id bigint,
    ersteller_id bigint,
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    kunde_id bigint,
    lieferant_id bigint,
    projekt_id bigint,
    beschreibung character varying(2000),
    farbe character varying(255),
    titel character varying(255) NOT NULL
);


--
-- Name: kalender_eintrag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kalender_eintrag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kalender_eintrag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kalender_eintrag_id_seq OWNED BY public.kalender_eintrag.id;


--
-- Name: kalender_eintrag_teilnehmer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kalender_eintrag_teilnehmer (
    kalender_eintrag_id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL
);


--
-- Name: kasse_einstellung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kasse_einstellung (
    ehegattengehalt_aktiv boolean DEFAULT false NOT NULL,
    ehegattengehalt_betrag numeric(10,2),
    ehegattengehalt_tag integer,
    mindestbestand numeric(10,2) DEFAULT 0.00 NOT NULL,
    wirtschaftsjahr_beginn_monat integer DEFAULT 1 NOT NULL,
    datev_mandantennummer character varying(5),
    datev_beraternummer character varying(7),
    letzte_buchung_jahrmonat character varying(7),
    aktualisiert_am timestamp(6) without time zone,
    bankkonto_nummer character varying(8) DEFAULT '1200'::character varying,
    id bigint NOT NULL,
    kassenkonto_nummer character varying(8) DEFAULT '1000'::character varying,
    privateinlage_sachkonto_id bigint,
    ehegattengehalt_empfaenger_name character varying(120)
);


--
-- Name: kasse_einstellung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kasse_einstellung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kasse_einstellung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kasse_einstellung_id_seq OWNED BY public.kasse_einstellung.id;


--
-- Name: kassenbuch_monatsabschluss; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kassenbuch_monatsabschluss (
    anfangsbestand numeric(15,2) NOT NULL,
    anzahl_belege integer NOT NULL,
    endbestand numeric(15,2) NOT NULL,
    jahr integer NOT NULL,
    monat integer NOT NULL,
    summe_ausgaben numeric(15,2) NOT NULL,
    summe_einnahmen numeric(15,2) NOT NULL,
    abgeschlossen_am timestamp(6) without time zone NOT NULL,
    abgeschlossen_von_id bigint,
    chain_index bigint,
    erste_laufende_nummer bigint,
    id bigint NOT NULL,
    letzte_laufende_nummer bigint,
    bemerkung character varying(1000),
    entry_hash character(64)
);


--
-- Name: kassenbuch_monatsabschluss_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kassenbuch_monatsabschluss_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kassenbuch_monatsabschluss_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kassenbuch_monatsabschluss_id_seq OWNED BY public.kassenbuch_monatsabschluss.id;


--
-- Name: kassenzaehlung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kassenzaehlung (
    differenz numeric(15,2) NOT NULL,
    gezaehlter_bestand numeric(15,2) NOT NULL,
    rechnerischer_bestand numeric(15,2) NOT NULL,
    stichtag date NOT NULL,
    ausgleich_beleg_id bigint,
    erfasst_von_id bigint,
    gezaehlt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    bemerkung character varying(1000),
    stueckelung_json text
);


--
-- Name: kassenzaehlung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kassenzaehlung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kassenzaehlung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kassenzaehlung_id_seq OWNED BY public.kassenzaehlung.id;


--
-- Name: kategorie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kategorie (
    id integer NOT NULL,
    parent_kategorie_id integer,
    beschreibung character varying(255)
);


--
-- Name: kategorie_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kategorie_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kategorie_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kategorie_id_seq OWNED BY public.kategorie.id;


--
-- Name: kategorie_rollen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kategorie_rollen (
    kategorie_id integer NOT NULL,
    rolle character varying(255) NOT NULL
);


--
-- Name: kontakt_rufnummer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kontakt_rufnummer (
    angelegt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    kunde_id bigint,
    lieferant_id bigint,
    steuerberater_id bigint,
    nummer_normalisiert character varying(40) NOT NULL,
    nummer_roh character varying(40) NOT NULL
);


--
-- Name: kontakt_rufnummer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kontakt_rufnummer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kontakt_rufnummer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kontakt_rufnummer_id_seq OWNED BY public.kontakt_rufnummer.id;


--
-- Name: kostenposition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kostenposition (
    abrechnungs_jahr integer NOT NULL,
    betrag numeric(19,2),
    buchungsdatum date,
    verbrauchsfaktor numeric(19,6),
    id bigint NOT NULL,
    kostenstelle_id bigint NOT NULL,
    verteilungsschluessel_id bigint,
    beleg_nummer character varying(255),
    berechnung character varying(255),
    beschreibung character varying(255)
);


--
-- Name: kostenposition_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kostenposition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kostenposition_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kostenposition_id_seq OWNED BY public.kostenposition.id;


--
-- Name: krankenkasse; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.krankenkasse (
    aktiv boolean DEFAULT true NOT NULL,
    gueltig_ab date,
    zusatzbeitrag_prozent numeric(5,2) NOT NULL,
    id bigint NOT NULL,
    kuerzel character varying(32),
    bemerkung character varying(500),
    name character varying(255) NOT NULL
);


--
-- Name: krankenkasse_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.krankenkasse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: krankenkasse_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.krankenkasse_id_seq OWNED BY public.krankenkasse.id;


--
-- Name: kunde; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kunde (
    zahlungsziel integer,
    id bigint NOT NULL,
    version bigint DEFAULT 0,
    anrede character varying(20),
    ansprechspartner character varying(255),
    kundennummer character varying(255) NOT NULL,
    mobiltelefon character varying(255),
    name character varying(255) NOT NULL,
    ort character varying(255),
    plz character varying(255),
    strasse character varying(255),
    telefon character varying(255)
);


--
-- Name: kunde_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kunde_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kunde_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kunde_id_seq OWNED BY public.kunde.id;


--
-- Name: kunde_notiz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kunde_notiz (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    kunde_id bigint NOT NULL,
    text text NOT NULL
);


--
-- Name: kunde_notiz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kunde_notiz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kunde_notiz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kunde_notiz_id_seq OWNED BY public.kunde_notiz.id;


--
-- Name: kunden_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kunden_emails (
    kunden_id bigint NOT NULL,
    email character varying(255) NOT NULL
);


--
-- Name: kunden_zaehler; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kunden_zaehler (
    id integer NOT NULL,
    naechste_nummer bigint NOT NULL
);


--
-- Name: langzeitkrankmeldung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.langzeitkrankmeldung (
    beginn date NOT NULL,
    ende date,
    lohnfortzahlung_bis date NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    notiz character varying(500),
    status character varying(255) NOT NULL
);


--
-- Name: langzeitkrankmeldung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.langzeitkrankmeldung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: langzeitkrankmeldung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.langzeitkrankmeldung_id_seq OWNED BY public.langzeitkrankmeldung.id;


--
-- Name: langzeitkrankmeldung_phase; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.langzeitkrankmeldung_phase (
    bis_datum date,
    stunden_pro_tag numeric(4,2),
    von_datum date NOT NULL,
    id bigint NOT NULL,
    langzeitkrankmeldung_id bigint NOT NULL,
    typ character varying(255) NOT NULL
);


--
-- Name: langzeitkrankmeldung_phase_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.langzeitkrankmeldung_phase_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: langzeitkrankmeldung_phase_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.langzeitkrankmeldung_phase_id_seq OWNED BY public.langzeitkrankmeldung_phase.id;


--
-- Name: leistung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leistung (
    preis numeric(19,2),
    id bigint NOT NULL,
    kategorie_id bigint,
    beschreibung text,
    bezeichnung character varying(255) NOT NULL,
    einheit character varying(255) NOT NULL
);


--
-- Name: leistung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.leistung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leistung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.leistung_id_seq OWNED BY public.leistung.id;


--
-- Name: lieferant_bild; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_bild (
    erstellt_am timestamp(6) without time zone,
    id bigint NOT NULL,
    lieferant_id bigint NOT NULL,
    mitarbeiter_id bigint,
    reklamation_id bigint,
    beschreibung character varying(255),
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255) NOT NULL
);


--
-- Name: lieferant_bild_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_bild_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_bild_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_bild_id_seq OWNED BY public.lieferant_bild.id;


--
-- Name: lieferant_dokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_dokument (
    ausgeblendet boolean DEFAULT false NOT NULL,
    attachment_id bigint,
    beleg_id bigint,
    id bigint NOT NULL,
    lieferant_id bigint NOT NULL,
    upload_datum timestamp(6) without time zone NOT NULL,
    uploaded_by_id bigint,
    version bigint DEFAULT 0,
    gespeicherter_dateiname character varying(255),
    original_dateiname character varying(255),
    typ character varying(255) NOT NULL
);


--
-- Name: lieferant_dokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_dokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_dokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_dokument_id_seq OWNED BY public.lieferant_dokument.id;


--
-- Name: lieferant_dokument_position; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_dokument_position (
    einzelpreis numeric(15,4),
    gesamtpreis_netto numeric(15,2),
    menge numeric(15,3),
    position_nr integer NOT NULL,
    geschaeftsdokument_id bigint NOT NULL,
    id bigint NOT NULL,
    kostenstelle_id bigint,
    projekt_id bigint,
    mengeneinheit character varying(20),
    preiseinheit character varying(20),
    externe_artikelnummer character varying(64),
    abmessung character varying(100),
    charge character varying(100),
    werkstoff character varying(100),
    bezeichnung character varying(500) NOT NULL,
    suchtext character varying(1000),
    positions_art character varying(255) DEFAULT 'WARE'::character varying NOT NULL
);


--
-- Name: lieferant_dokument_position_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_dokument_position_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_dokument_position_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_dokument_position_id_seq OWNED BY public.lieferant_dokument_position.id;


--
-- Name: lieferant_dokument_projekt_anteil; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_dokument_projekt_anteil (
    absoluter_betrag numeric(12,2),
    berechneter_betrag numeric(12,2),
    prozent integer,
    streckung_jahre integer NOT NULL,
    streckung_start_jahr integer,
    dokument_id bigint NOT NULL,
    id bigint NOT NULL,
    kostenstelle_id bigint,
    projekt_id bigint,
    zugeordnet_am timestamp(6) without time zone,
    zugeordnet_von_user_id bigint,
    beschreibung character varying(255)
);


--
-- Name: lieferant_dokument_projekt_anteil_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_dokument_projekt_anteil_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_dokument_projekt_anteil_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_dokument_projekt_anteil_id_seq OWNED BY public.lieferant_dokument_projekt_anteil.id;


--
-- Name: lieferant_dokument_verknuepfung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_dokument_verknuepfung (
    dokument_id bigint NOT NULL,
    verknuepft_id bigint NOT NULL
);


--
-- Name: lieferant_dokument_verknuepfung_gesperrt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_dokument_verknuepfung_gesperrt (
    dokument_id bigint NOT NULL,
    gesperrt_am timestamp(6) without time zone NOT NULL,
    verknuepft_id bigint NOT NULL
);


--
-- Name: lieferant_geschaeftsdokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_geschaeftsdokument (
    ai_confidence double precision,
    bereits_gezahlt boolean,
    betrag_brutto numeric(12,2),
    betrag_netto numeric(12,2),
    bezahlt boolean NOT NULL,
    bezahlt_am date,
    dokument_datum date,
    genehmigt boolean NOT NULL,
    lagerbestellung boolean NOT NULL,
    liefertermin date,
    manuelle_pruefung_erforderlich boolean NOT NULL,
    mit_skonto boolean,
    mwst_satz numeric(5,4),
    netto_tage integer,
    skonto_prozent numeric(5,2),
    skonto_tage integer,
    tatsaechlich_gezahlt numeric(12,2),
    verifiziert boolean,
    zahlungsziel date,
    analysiert_am timestamp(6) without time zone,
    id bigint NOT NULL,
    bestellnummer character varying(50),
    dokument_nummer character varying(50),
    referenz_nummer character varying(50),
    zahlungsart character varying(50),
    ai_raw_json text,
    datenquelle character varying(255)
);


--
-- Name: lieferant_notiz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_notiz (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    lieferant_id bigint NOT NULL,
    text text NOT NULL
);


--
-- Name: lieferant_notiz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_notiz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_notiz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_notiz_id_seq OWNED BY public.lieferant_notiz.id;


--
-- Name: lieferant_reklamation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferant_reklamation (
    erstellt_am timestamp(6) without time zone NOT NULL,
    erstellt_von_id bigint,
    id bigint NOT NULL,
    lieferant_id bigint NOT NULL,
    lieferschein_id bigint,
    version bigint DEFAULT 0,
    beschreibung text,
    status character varying(255) NOT NULL
);


--
-- Name: lieferant_reklamation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferant_reklamation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferant_reklamation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferant_reklamation_id_seq OWNED BY public.lieferant_reklamation.id;


--
-- Name: lieferanten; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferanten (
    bestellungen integer DEFAULT 0,
    ist_aktiv boolean,
    vorauskasse boolean DEFAULT false NOT NULL,
    id bigint NOT NULL,
    standard_kostenstelle_id bigint,
    start_zusammenarbeit timestamp(6) without time zone,
    version bigint DEFAULT 0,
    alias_name character varying(255),
    eigene_kundennummer character varying(255),
    lieferanten_typ character varying(255),
    lieferantenname character varying(255) NOT NULL,
    mobiltelefon character varying(255),
    ort character varying(255),
    plz character varying(255),
    strasse character varying(255),
    telefon character varying(255),
    vertreter character varying(255)
);


--
-- Name: lieferanten_artikel_preise; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferanten_artikel_preise (
    aktuell boolean DEFAULT true NOT NULL,
    preis numeric(19,4),
    artikel_id bigint,
    erfasst_am timestamp(6) without time zone,
    id bigint NOT NULL,
    lieferant_id bigint,
    preis_aenderungsdatum timestamp(6) without time zone,
    externe_artikelnummer character varying(255),
    notiz character varying(255),
    quelle character varying(255) DEFAULT 'UNBEKANNT'::character varying NOT NULL
);


--
-- Name: lieferanten_artikel_preise_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferanten_artikel_preise_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferanten_artikel_preise_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferanten_artikel_preise_id_seq OWNED BY public.lieferanten_artikel_preise.id;


--
-- Name: lieferanten_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferanten_emails (
    lieferanten_id bigint NOT NULL,
    email character varying(255)
);


--
-- Name: lieferanten_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lieferanten_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lieferanten_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lieferanten_id_seq OWNED BY public.lieferanten.id;


--
-- Name: lieferanten_rollen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lieferanten_rollen (
    lieferant_id bigint NOT NULL,
    rolle character varying(255) NOT NULL
);


--
-- Name: lohnabrechnung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lohnabrechnung (
    bruttolohn numeric(10,2),
    jahr integer NOT NULL,
    monat integer NOT NULL,
    nettolohn numeric(10,2),
    email_id bigint,
    id bigint NOT NULL,
    import_datum timestamp(6) without time zone NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    steuerberater_id bigint,
    ai_raw_json text,
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255),
    status character varying(255) NOT NULL
);


--
-- Name: lohnabrechnung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lohnabrechnung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lohnabrechnung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lohnabrechnung_id_seq OWNED BY public.lohnabrechnung.id;


--
-- Name: materialkosten; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.materialkosten (
    betrag numeric(19,2) NOT NULL,
    monat integer,
    id bigint NOT NULL,
    lieferant_id bigint,
    projekt_id bigint NOT NULL,
    beschreibung character varying(255),
    externe_artikelnummer character varying(255),
    rechnungsnummer character varying(255)
);


--
-- Name: materialkosten_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materialkosten_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materialkosten_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.materialkosten_id_seq OWNED BY public.materialkosten.id;


--
-- Name: miete_kostenstelle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.miete_kostenstelle (
    umlagefaehig boolean NOT NULL,
    id bigint NOT NULL,
    mietobjekt_id bigint NOT NULL,
    standard_schluessel_id bigint,
    beschreibung character varying(255),
    name character varying(255) NOT NULL
);


--
-- Name: miete_kostenstelle_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.miete_kostenstelle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: miete_kostenstelle_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.miete_kostenstelle_id_seq OWNED BY public.miete_kostenstelle.id;


--
-- Name: mietobjekt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mietobjekt (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    ort character varying(255),
    plz character varying(255),
    strasse character varying(255)
);


--
-- Name: mietobjekt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mietobjekt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mietobjekt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mietobjekt_id_seq OWNED BY public.mietobjekt.id;


--
-- Name: mietpartei; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mietpartei (
    monatlicher_vorschuss numeric(19,2),
    id bigint NOT NULL,
    mietobjekt_id bigint NOT NULL,
    email character varying(255),
    name character varying(255) NOT NULL,
    rolle character varying(255) NOT NULL,
    telefon character varying(255)
);


--
-- Name: mietpartei_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mietpartei_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mietpartei_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mietpartei_id_seq OWNED BY public.mietpartei.id;


--
-- Name: mitarbeiter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mitarbeiter (
    aktiv boolean NOT NULL,
    eintrittsdatum date,
    fuehrt_zeitkonto boolean DEFAULT true NOT NULL,
    geburtstag date,
    geldwert_vorteil_monat numeric(12,2),
    ist_geschaeftsfuehrer boolean DEFAULT false NOT NULL,
    jahres_urlaub integer,
    kalkulatorischer_lohn_monat numeric(12,2),
    kinderlos boolean DEFAULT false NOT NULL,
    resturlaub_vorjahr integer,
    stundenlohn numeric(10,2),
    urlaubs_korrektur integer,
    id bigint NOT NULL,
    krankenkasse_id bigint,
    version bigint DEFAULT 0,
    art character varying(255) DEFAULT 'MENSCH'::character varying NOT NULL,
    beschaeftigungsart character varying(255) DEFAULT 'REGULAER'::character varying NOT NULL,
    email character varying(255),
    festnetz character varying(255),
    login_token character varying(255),
    nachname character varying(255) NOT NULL,
    ort character varying(255),
    plz character varying(255),
    qualifikation character varying(255),
    strasse character varying(255),
    telefon character varying(255),
    vorname character varying(255) NOT NULL
);


--
-- Name: mitarbeiter_abteilung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mitarbeiter_abteilung (
    abteilung_id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL
);


--
-- Name: mitarbeiter_dokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mitarbeiter_dokument (
    email_versand_datum date,
    upload_datum date,
    dateigroesse bigint,
    id bigint NOT NULL,
    mitarbeiter_id bigint,
    dateityp character varying(255),
    dokument_gruppe character varying(255) NOT NULL,
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255) NOT NULL
);


--
-- Name: mitarbeiter_dokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mitarbeiter_dokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mitarbeiter_dokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mitarbeiter_dokument_id_seq OWNED BY public.mitarbeiter_dokument.id;


--
-- Name: mitarbeiter_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mitarbeiter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mitarbeiter_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mitarbeiter_id_seq OWNED BY public.mitarbeiter.id;


--
-- Name: mitarbeiter_notiz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mitarbeiter_notiz (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint,
    inhalt text
);


--
-- Name: mitarbeiter_notiz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mitarbeiter_notiz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mitarbeiter_notiz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mitarbeiter_notiz_id_seq OWNED BY public.mitarbeiter_notiz.id;


--
-- Name: mitarbeiter_stundenlohn; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mitarbeiter_stundenlohn (
    gueltig_ab date NOT NULL,
    stundenlohn numeric(10,2) NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    bemerkung character varying(500)
);


--
-- Name: mitarbeiter_stundenlohn_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mitarbeiter_stundenlohn_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mitarbeiter_stundenlohn_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mitarbeiter_stundenlohn_id_seq OWNED BY public.mitarbeiter_stundenlohn.id;


--
-- Name: monats_saldo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monats_saldo (
    abwesenheits_stunden numeric(10,2) NOT NULL,
    feiertags_stunden numeric(10,2) NOT NULL,
    festgeschrieben boolean DEFAULT false NOT NULL,
    fortbildung_stunden numeric(10,2),
    gueltig boolean NOT NULL,
    ist_stunden numeric(10,2) NOT NULL,
    jahr integer NOT NULL,
    korrektur_stunden numeric(10,2) NOT NULL,
    krankengeld_stunden numeric(10,2),
    krankheit_stunden numeric(10,2),
    monat integer NOT NULL,
    soll_stunden numeric(10,2) NOT NULL,
    urlaub_stunden numeric(10,2),
    wiedereingliederung_stunden numeric(10,2),
    zeitausgleich_stunden numeric(10,2),
    berechnet_am timestamp(6) without time zone NOT NULL,
    festgeschrieben_am timestamp(6) without time zone,
    festgeschrieben_von_mitarbeiter_id bigint,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: monats_saldo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.monats_saldo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: monats_saldo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.monats_saldo_id_seq OWNED BY public.monats_saldo.id;


--
-- Name: monatsabschluss_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monatsabschluss_audit (
    jahr integer NOT NULL,
    monat integer NOT NULL,
    akteur_id bigint NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    zeitpunkt timestamp(6) without time zone NOT NULL,
    aktion character varying(255) NOT NULL
);


--
-- Name: monatsabschluss_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.monatsabschluss_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: monatsabschluss_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.monatsabschluss_audit_id_seq OWNED BY public.monatsabschluss_audit.id;


--
-- Name: ooo_reply_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ooo_reply_log (
    id bigint NOT NULL,
    replied_at timestamp(6) without time zone NOT NULL,
    schedule_id bigint NOT NULL,
    sender_address character varying(320) NOT NULL
);


--
-- Name: ooo_reply_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ooo_reply_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ooo_reply_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ooo_reply_log_id_seq OWNED BY public.ooo_reply_log.id;


--
-- Name: out_of_office_schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.out_of_office_schedule (
    active boolean NOT NULL,
    end_at date NOT NULL,
    start_at date NOT NULL,
    id bigint NOT NULL,
    signature_id bigint,
    title character varying(200) NOT NULL,
    subject_template character varying(300),
    body_template text
);


--
-- Name: out_of_office_schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.out_of_office_schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: out_of_office_schedule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.out_of_office_schedule_id_seq OWNED BY public.out_of_office_schedule.id;


--
-- Name: produktkategorie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.produktkategorie (
    id bigint NOT NULL,
    parent_kategorie_id bigint,
    version bigint DEFAULT 0,
    beschreibung text,
    bezeichnung character varying(255) NOT NULL,
    bild_url character varying(255),
    verrechnungseinheit character varying(255) NOT NULL
);


--
-- Name: produktkategorie_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.produktkategorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: produktkategorie_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.produktkategorie_id_seq OWNED BY public.produktkategorie.id;


--
-- Name: projekt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt (
    abgeschlossen boolean NOT NULL,
    abgeschlossen_manuell boolean DEFAULT false NOT NULL,
    abschlussdatum date,
    anlegedatum date NOT NULL,
    bezahlt boolean NOT NULL,
    brutto_preis numeric(38,2) NOT NULL,
    id bigint NOT NULL,
    kunden_id bigint,
    version bigint DEFAULT 0,
    auftragsnummer character varying(255) NOT NULL,
    bauvorhaben character varying(255) NOT NULL,
    bild_url character varying(255),
    kurzbeschreibung text,
    ort character varying(255),
    plz character varying(255),
    projekt_art character varying(255) NOT NULL,
    strasse character varying(255)
);


--
-- Name: projekt_dokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_dokument (
    email_versand_datum date,
    upload_datum date,
    dateigroesse bigint,
    id bigint NOT NULL,
    lieferant_id bigint,
    projekt bigint,
    uploaded_by_id bigint,
    dateityp character varying(255),
    dokument_gruppe character varying(255) NOT NULL,
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255) NOT NULL
);


--
-- Name: projekt_dokument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.projekt_dokument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projekt_dokument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.projekt_dokument_id_seq OWNED BY public.projekt_dokument.id;


--
-- Name: projekt_geschaeftsdokument; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_geschaeftsdokument (
    bezahlt boolean NOT NULL,
    brutto_betrag numeric(38,2),
    faelligkeitsdatum date,
    rechnungsdatum date,
    system_generiert boolean DEFAULT false NOT NULL,
    id bigint NOT NULL,
    referenz_dokument_id bigint,
    dokumentid character varying(255) NOT NULL,
    geschaeftsdokumentart character varying(255) NOT NULL,
    mahnstufe character varying(255)
);


--
-- Name: projekt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.projekt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projekt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.projekt_id_seq OWNED BY public.projekt.id;


--
-- Name: projekt_kunden_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_kunden_emails (
    projekt_id bigint NOT NULL,
    email character varying(255)
);


--
-- Name: projekt_notiz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_notiz (
    mobile_sichtbar boolean NOT NULL,
    nur_fuer_ersteller boolean NOT NULL,
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    projekt_id bigint NOT NULL,
    notiz character varying(4000) NOT NULL
);


--
-- Name: projekt_notiz_bild; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_notiz_bild (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    notiz_id bigint NOT NULL,
    dateityp character varying(255),
    gespeicherter_dateiname character varying(255) NOT NULL,
    original_dateiname character varying(255)
);


--
-- Name: projekt_notiz_bild_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.projekt_notiz_bild_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projekt_notiz_bild_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.projekt_notiz_bild_id_seq OWNED BY public.projekt_notiz_bild.id;


--
-- Name: projekt_notiz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.projekt_notiz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projekt_notiz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.projekt_notiz_id_seq OWNED BY public.projekt_notiz.id;


--
-- Name: projekt_produktkategorie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projekt_produktkategorie (
    menge numeric(19,2) NOT NULL,
    id bigint NOT NULL,
    produktkategorie_id bigint NOT NULL,
    projekt_id bigint NOT NULL
);


--
-- Name: projekt_produktkategorie_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.projekt_produktkategorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projekt_produktkategorie_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.projekt_produktkategorie_id_seq OWNED BY public.projekt_produktkategorie.id;


--
-- Name: push_subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.push_subscription (
    erstellt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    auth character varying(512) NOT NULL,
    p256dh character varying(512) NOT NULL,
    endpoint character varying(2048) NOT NULL
);


--
-- Name: push_subscription_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.push_subscription_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: push_subscription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.push_subscription_id_seq OWNED BY public.push_subscription.id;


--
-- Name: raum; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.raum (
    flaeche_quadratmeter numeric(38,2),
    id bigint NOT NULL,
    mietobjekt_id bigint NOT NULL,
    beschreibung character varying(255),
    name character varying(255) NOT NULL
);


--
-- Name: raum_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.raum_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: raum_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.raum_id_seq OWNED BY public.raum.id;


--
-- Name: sachkonto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sachkonto (
    aktiv boolean DEFAULT true NOT NULL,
    sortierung integer DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    konto_typ character varying(20) NOT NULL,
    nummer character varying(20),
    bezeichnung character varying(120) NOT NULL,
    beschreibung character varying(500)
);


--
-- Name: sachkonto_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sachkonto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sachkonto_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sachkonto_id_seq OWNED BY public.sachkonto.id;


--
-- Name: schnittbilder; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schnittbilder (
    kategorie_id integer NOT NULL,
    id bigint NOT NULL,
    bild_url_schnittbild character varying(255) NOT NULL,
    form character varying(255) NOT NULL
);


--
-- Name: schnittbilder_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.schnittbilder_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schnittbilder_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.schnittbilder_id_seq OWNED BY public.schnittbilder.id;


--
-- Name: seen_sender_domain; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seen_sender_domain (
    email_count integer DEFAULT 1 NOT NULL,
    first_seen timestamp(6) without time zone NOT NULL,
    domain character varying(255) NOT NULL
);


--
-- Name: spam_model_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spam_model_stats (
    id bigint NOT NULL,
    stat_value bigint DEFAULT 0 NOT NULL,
    stat_key character varying(50) NOT NULL
);


--
-- Name: spam_model_stats_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.spam_model_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spam_model_stats_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.spam_model_stats_id_seq OWNED BY public.spam_model_stats.id;


--
-- Name: spam_token_count; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spam_token_count (
    ham_count integer DEFAULT 0 NOT NULL,
    spam_count integer DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    token character varying(100) NOT NULL
);


--
-- Name: spam_token_count_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.spam_token_count_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spam_token_count_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.spam_token_count_id_seq OWNED BY public.spam_token_count.id;


--
-- Name: sprachnachricht; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sprachnachricht (
    anrufbeantworter integer NOT NULL,
    dauer_sekunden integer DEFAULT 0 NOT NULL,
    abgehoert_am timestamp(6) without time zone,
    abgehoert_von bigint,
    angelegt_am timestamp(6) without time zone NOT NULL,
    anruf_id bigint,
    id bigint NOT NULL,
    kunde_id bigint,
    lieferant_id bigint,
    steuerberater_id bigint,
    zeitpunkt timestamp(6) without time zone NOT NULL,
    nummer_normalisiert character varying(40),
    nummer_roh character varying(40) DEFAULT ''::character varying NOT NULL,
    datei_name character varying(100) NOT NULL,
    zuordnung character varying(255) DEFAULT 'KEINE'::character varying NOT NULL
);


--
-- Name: sprachnachricht_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sprachnachricht_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sprachnachricht_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sprachnachricht_id_seq OWNED BY public.sprachnachricht.id;


--
-- Name: steuerberater_ansprechpartner; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.steuerberater_ansprechpartner (
    ist_lohn_ansprechpartner boolean DEFAULT false NOT NULL,
    id bigint NOT NULL,
    steuerberater_id bigint NOT NULL,
    anrede character varying(32),
    notizen character varying(500),
    email character varying(255),
    nachname character varying(255) NOT NULL,
    telefon character varying(255),
    vorname character varying(255)
);


--
-- Name: steuerberater_ansprechpartner_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.steuerberater_ansprechpartner_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: steuerberater_ansprechpartner_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.steuerberater_ansprechpartner_id_seq OWNED BY public.steuerberater_ansprechpartner.id;


--
-- Name: steuerberater_kontakt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.steuerberater_kontakt (
    aktiv boolean NOT NULL,
    auto_process_emails boolean NOT NULL,
    gueltig_ab date,
    gueltig_bis date,
    id bigint NOT NULL,
    notizen character varying(500),
    ansprechpartner character varying(255),
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    telefon character varying(255)
);


--
-- Name: steuerberater_kontakt_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.steuerberater_kontakt_emails (
    steuerberater_id bigint NOT NULL,
    email character varying(255)
);


--
-- Name: steuerberater_kontakt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.steuerberater_kontakt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: steuerberater_kontakt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.steuerberater_kontakt_id_seq OWNED BY public.steuerberater_kontakt.id;


--
-- Name: sv_satz; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sv_satz (
    gueltig_ab date NOT NULL,
    prozent numeric(5,2) NOT NULL,
    id bigint NOT NULL,
    beschreibung character varying(500),
    satz_typ character varying(255) NOT NULL
);


--
-- Name: sv_satz_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sv_satz_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sv_satz_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sv_satz_id_seq OWNED BY public.sv_satz.id;


--
-- Name: system_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_setting (
    setting_key character varying(128) NOT NULL,
    beschreibung character varying(255),
    setting_value text
);


--
-- Name: telefon_anruf; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.telefon_anruf (
    anrufbeantworter integer,
    dauer_minuten integer DEFAULT 0 NOT NULL,
    angelegt_am timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    kunde_id bigint,
    lieferant_id bigint,
    steuerberater_id bigint,
    zeitpunkt timestamp(6) without time zone NOT NULL,
    eigene_nummer character varying(40) NOT NULL,
    nummer_normalisiert character varying(40),
    nummer_roh character varying(40) DEFAULT ''::character varying NOT NULL,
    name_fritzbox character varying(200),
    art character varying(255) NOT NULL,
    zuordnung character varying(255) DEFAULT 'KEINE'::character varying NOT NULL
);


--
-- Name: telefon_anruf_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.telefon_anruf_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: telefon_anruf_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.telefon_anruf_id_seq OWNED BY public.telefon_anruf.id;


--
-- Name: textbaustein; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.textbaustein (
    sort_order integer,
    created_at timestamp(6) with time zone,
    id bigint NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint DEFAULT 0,
    typ character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    beschreibung character varying(500),
    html text
);


--
-- Name: textbaustein_dokumenttyp_enum; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.textbaustein_dokumenttyp_enum (
    textbaustein_id bigint NOT NULL,
    dokumenttyp character varying(30)
);


--
-- Name: textbaustein_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.textbaustein_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: textbaustein_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.textbaustein_id_seq OWNED BY public.textbaustein.id;


--
-- Name: textbaustein_placeholder; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.textbaustein_placeholder (
    textbaustein_id bigint NOT NULL,
    placeholder character varying(120)
);


--
-- Name: urlaubsantrag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.urlaubsantrag (
    bis_datum date NOT NULL,
    von_datum date NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    bemerkung character varying(2000),
    status character varying(255) NOT NULL,
    typ character varying(255) NOT NULL
);


--
-- Name: urlaubsantrag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.urlaubsantrag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: urlaubsantrag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.urlaubsantrag_id_seq OWNED BY public.urlaubsantrag.id;


--
-- Name: verbrauchsgegenstand; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verbrauchsgegenstand (
    aktiv boolean NOT NULL,
    id bigint NOT NULL,
    raum_id bigint NOT NULL,
    einheit character varying(255),
    name character varying(255) NOT NULL,
    seriennummer character varying(255),
    verbrauchsart character varying(255) NOT NULL
);


--
-- Name: verbrauchsgegenstand_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.verbrauchsgegenstand_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: verbrauchsgegenstand_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.verbrauchsgegenstand_id_seq OWNED BY public.verbrauchsgegenstand.id;


--
-- Name: verteilungsschluessel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verteilungsschluessel (
    id bigint NOT NULL,
    mietobjekt_id bigint NOT NULL,
    beschreibung character varying(255),
    name character varying(255) NOT NULL,
    typ character varying(255) NOT NULL
);


--
-- Name: verteilungsschluessel_eintrag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verteilungsschluessel_eintrag (
    anteil numeric(10,4) NOT NULL,
    id bigint NOT NULL,
    mietpartei_id bigint NOT NULL,
    verbrauchsgegenstand_id bigint,
    verteilungsschluessel_id bigint NOT NULL,
    kommentar character varying(255)
);


--
-- Name: verteilungsschluessel_eintrag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.verteilungsschluessel_eintrag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: verteilungsschluessel_eintrag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.verteilungsschluessel_eintrag_id_seq OWNED BY public.verteilungsschluessel_eintrag.id;


--
-- Name: verteilungsschluessel_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.verteilungsschluessel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: verteilungsschluessel_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.verteilungsschluessel_id_seq OWNED BY public.verteilungsschluessel.id;


--
-- Name: website_analytics_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_analytics_snapshot (
    conversion integer DEFAULT 0 NOT NULL,
    schema_version integer NOT NULL,
    snapshot_date date NOT NULL,
    generated_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    received_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    totals_leads_mail bigint DEFAULT 0 NOT NULL,
    totals_leads_phone bigint DEFAULT 0 NOT NULL,
    totals_pageviews bigint DEFAULT 0 NOT NULL,
    totals_submissions bigint DEFAULT 0 NOT NULL,
    totals_visitors bigint DEFAULT 0 NOT NULL,
    visitors_today bigint DEFAULT 0 NOT NULL,
    visitors_yesterday bigint DEFAULT 0 NOT NULL,
    browsers_json text,
    cities_json text,
    devices_json text,
    funnel_json text,
    raw_payload text,
    top_pages_json text
);


--
-- Name: website_analytics_snapshot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.website_analytics_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: website_analytics_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.website_analytics_snapshot_id_seq OWNED BY public.website_analytics_snapshot.id;


--
-- Name: werkstoff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.werkstoff (
    dichte numeric(6,3),
    pulverbeschichtungsgeeignet boolean DEFAULT false NOT NULL,
    verzinkungsgeeignet boolean DEFAULT false NOT NULL,
    id bigint NOT NULL,
    anzeigename character varying(255),
    beschichtungshinweis character varying(255),
    name character varying(255),
    werkstattname character varying(255),
    werkstoffnorm character varying(255)
);


--
-- Name: werkstoff_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.werkstoff_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: werkstoff_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.werkstoff_id_seq OWNED BY public.werkstoff.id;


--
-- Name: zaehlerstand; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zaehlerstand (
    abrechnungs_jahr integer NOT NULL,
    stand numeric(19,4) NOT NULL,
    stichtag date NOT NULL,
    verbrauch numeric(19,4),
    erfasst_am timestamp(6) with time zone,
    id bigint NOT NULL,
    verbrauchsgegenstand_id bigint NOT NULL,
    kommentar character varying(255)
);


--
-- Name: zaehlerstand_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zaehlerstand_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zaehlerstand_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zaehlerstand_id_seq OWNED BY public.zaehlerstand.id;


--
-- Name: zahlungsart; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zahlungsart (
    aktiv boolean DEFAULT true NOT NULL,
    sortierung integer DEFAULT 0 NOT NULL,
    id bigint NOT NULL,
    bezeichnung character varying(60) NOT NULL
);


--
-- Name: zahlungsart_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zahlungsart_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zahlungsart_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zahlungsart_id_seq OWNED BY public.zahlungsart.id;


--
-- Name: zeitbuchung; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitbuchung (
    anzahl_in_stunden numeric(10,2),
    automatisch_beendet boolean DEFAULT false NOT NULL,
    version integer NOT NULL,
    arbeitsgang_id bigint,
    arbeitsgang_stundensatz_id bigint,
    ende_zeit timestamp(6) without time zone,
    erfasst_am timestamp(6) without time zone,
    erfasst_von_mitarbeiter_id bigint,
    id bigint NOT NULL,
    mitarbeiter_id bigint,
    projekt_id bigint,
    projekt_produktkategorie_id bigint,
    start_zeit timestamp(6) without time zone,
    zuletzt_geaendert_am timestamp(6) without time zone,
    zuletzt_geaendert_von bigint,
    typ character varying(20),
    idempotency_key character varying(36),
    stop_idempotency_key character varying(36),
    erfasst_via character varying(50),
    notiz character varying(500)
);


--
-- Name: zeitbuchung_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitbuchung_audit (
    anzahl_in_stunden numeric(10,2),
    version integer NOT NULL,
    arbeitsgang_id bigint,
    arbeitsgang_stundensatz_id bigint,
    ende_zeit timestamp(6) without time zone,
    geaendert_am timestamp(6) without time zone NOT NULL,
    geaendert_von_mitarbeiter_id bigint NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    projekt_id bigint,
    projekt_produktkategorie_id bigint,
    start_zeit timestamp(6) without time zone NOT NULL,
    zeitbuchung_id bigint NOT NULL,
    aktion character varying(20) NOT NULL,
    geaendert_via character varying(50) NOT NULL,
    aenderungsgrund text,
    notiz text
);


--
-- Name: zeitbuchung_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitbuchung_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitbuchung_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitbuchung_audit_id_seq OWNED BY public.zeitbuchung_audit.id;


--
-- Name: zeitbuchung_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitbuchung_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitbuchung_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitbuchung_id_seq OWNED BY public.zeitbuchung.id;


--
-- Name: zeitkontenmodell; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitkontenmodell (
    buchung_ende_zeit time(6) without time zone,
    buchung_start_zeit time(6) without time zone,
    dienstag_stunden numeric(4,2),
    donnerstag_stunden numeric(4,2),
    freitag_stunden numeric(4,2),
    mittwoch_stunden numeric(4,2),
    montag_stunden numeric(4,2),
    samstag_stunden numeric(4,2),
    sonntag_stunden numeric(4,2),
    id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    bezeichnung character varying(255) NOT NULL
);


--
-- Name: zeitkontenmodell_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitkontenmodell_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitkontenmodell_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitkontenmodell_id_seq OWNED BY public.zeitkontenmodell.id;


--
-- Name: zeitkonto_korrektur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitkonto_korrektur (
    datum date NOT NULL,
    storniert boolean NOT NULL,
    stunden numeric(10,2) NOT NULL,
    version integer NOT NULL,
    erstellt_am timestamp(6) without time zone NOT NULL,
    erstellt_von_id bigint,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    storniert_am timestamp(6) without time zone,
    storniert_von_id bigint,
    grund character varying(500) NOT NULL,
    stornierungsgrund character varying(500),
    typ character varying(255) NOT NULL
);


--
-- Name: zeitkonto_korrektur_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitkonto_korrektur_audit (
    datum date NOT NULL,
    stunden numeric(10,2) NOT NULL,
    version integer NOT NULL,
    geaendert_am timestamp(6) without time zone NOT NULL,
    geaendert_von_mitarbeiter_id bigint NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    zeitkonto_korrektur_id bigint NOT NULL,
    aktion character varying(20) NOT NULL,
    geaendert_via character varying(50) NOT NULL,
    aenderungsgrund text,
    grund text
);


--
-- Name: zeitkonto_korrektur_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitkonto_korrektur_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitkonto_korrektur_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitkonto_korrektur_audit_id_seq OWNED BY public.zeitkonto_korrektur_audit.id;


--
-- Name: zeitkonto_korrektur_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitkonto_korrektur_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitkonto_korrektur_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitkonto_korrektur_id_seq OWNED BY public.zeitkonto_korrektur.id;


--
-- Name: zeitkonto_pause; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitkonto_pause (
    gueltig_bis date,
    gueltig_von date NOT NULL,
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_zeitkonto_pause_zeitraum CHECK (((gueltig_bis IS NULL) OR (gueltig_bis >= gueltig_von)))
);


--
-- Name: zeitkonto_pause_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitkonto_pause_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitkonto_pause_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitkonto_pause_id_seq OWNED BY public.zeitkonto_pause.id;


--
-- Name: zeitkonto_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zeitkonto_version (
    buchung_ende_zeit time(6) without time zone,
    buchung_start_zeit time(6) without time zone,
    dienstag_stunden numeric(4,2),
    donnerstag_stunden numeric(4,2),
    freitag_stunden numeric(4,2),
    gueltig_bis date,
    gueltig_von date NOT NULL,
    mittwoch_stunden numeric(4,2),
    montag_stunden numeric(4,2),
    samstag_stunden numeric(4,2),
    sonntag_stunden numeric(4,2),
    id bigint NOT NULL,
    mitarbeiter_id bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    vorlage_id bigint,
    CONSTRAINT chk_zeitkonto_version_zeitraum CHECK (((gueltig_bis IS NULL) OR (gueltig_bis >= gueltig_von)))
);


--
-- Name: zeitkonto_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zeitkonto_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zeitkonto_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zeitkonto_version_id_seq OWNED BY public.zeitkonto_version.id;


--
-- Name: abteilung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung ALTER COLUMN id SET DEFAULT nextval('public.abteilung_id_seq'::regclass);


--
-- Name: abteilung_dokument_berechtigung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung_dokument_berechtigung ALTER COLUMN id SET DEFAULT nextval('public.abteilung_dokument_berechtigung_id_seq'::regclass);


--
-- Name: abwesenheit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit ALTER COLUMN id SET DEFAULT nextval('public.abwesenheit_id_seq'::regclass);


--
-- Name: aenderungsgrund_katalog id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aenderungsgrund_katalog ALTER COLUMN id SET DEFAULT nextval('public.aenderungsgrund_katalog_id_seq'::regclass);


--
-- Name: anfrage id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage ALTER COLUMN id SET DEFAULT nextval('public.anfrage_id_seq'::regclass);


--
-- Name: anfrage_dokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_dokument ALTER COLUMN id SET DEFAULT nextval('public.anfrage_dokument_id_seq'::regclass);


--
-- Name: anfrage_notiz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz ALTER COLUMN id SET DEFAULT nextval('public.anfrage_notiz_id_seq'::regclass);


--
-- Name: anfrage_notiz_bild id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz_bild ALTER COLUMN id SET DEFAULT nextval('public.anfrage_notiz_bild_id_seq'::regclass);


--
-- Name: arbeitsgang id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang ALTER COLUMN id SET DEFAULT nextval('public.arbeitsgang_id_seq'::regclass);


--
-- Name: arbeitsgang_stundensatz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang_stundensatz ALTER COLUMN id SET DEFAULT nextval('public.arbeitsgang_stundensatz_id_seq'::regclass);


--
-- Name: arbeitszeitart id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitszeitart ALTER COLUMN id SET DEFAULT nextval('public.arbeitszeitart_id_seq'::regclass);


--
-- Name: artikel id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel ALTER COLUMN id SET DEFAULT nextval('public.artikel_id_seq'::regclass);


--
-- Name: artikel_dokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_dokument ALTER COLUMN id SET DEFAULT nextval('public.artikel_dokument_id_seq'::regclass);


--
-- Name: artikel_in_projekt id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt ALTER COLUMN id SET DEFAULT nextval('public.artikel_in_projekt_id_seq'::regclass);


--
-- Name: ausgangs_geschaeftsdokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument ALTER COLUMN id SET DEFAULT nextval('public.ausgangs_geschaeftsdokument_id_seq'::regclass);


--
-- Name: ausgangs_geschaeftsdokument_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument_audit ALTER COLUMN id SET DEFAULT nextval('public.ausgangs_geschaeftsdokument_audit_id_seq'::regclass);


--
-- Name: ausgangs_geschaeftsdokument_counter id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument_counter ALTER COLUMN id SET DEFAULT nextval('public.ausgangs_geschaeftsdokument_counter_id_seq'::regclass);


--
-- Name: beleg id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg ALTER COLUMN id SET DEFAULT nextval('public.beleg_id_seq'::regclass);


--
-- Name: beleg_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_audit ALTER COLUMN id SET DEFAULT nextval('public.beleg_audit_id_seq'::regclass);


--
-- Name: beleg_kostenstellen_anteil id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_kostenstellen_anteil ALTER COLUMN id SET DEFAULT nextval('public.beleg_kostenstellen_anteil_id_seq'::regclass);


--
-- Name: beleg_position id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_position ALTER COLUMN id SET DEFAULT nextval('public.beleg_position_id_seq'::regclass);


--
-- Name: bwa_position id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_position ALTER COLUMN id SET DEFAULT nextval('public.bwa_position_id_seq'::regclass);


--
-- Name: bwa_upload id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_upload ALTER COLUMN id SET DEFAULT nextval('public.bwa_upload_id_seq'::regclass);


--
-- Name: datensatz_lock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datensatz_lock ALTER COLUMN id SET DEFAULT nextval('public.datensatz_lock_id_seq'::regclass);


--
-- Name: dokument_freigabe id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokument_freigabe ALTER COLUMN id SET DEFAULT nextval('public.dokument_freigabe_id_seq'::regclass);


--
-- Name: dokumentnummer_counter id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokumentnummer_counter ALTER COLUMN id SET DEFAULT nextval('public.dokumentnummer_counter_id_seq'::regclass);


--
-- Name: email id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email ALTER COLUMN id SET DEFAULT nextval('public.email_id_seq'::regclass);


--
-- Name: email_absender id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_absender ALTER COLUMN id SET DEFAULT nextval('public.email_absender_id_seq'::regclass);


--
-- Name: email_attachment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_attachment ALTER COLUMN id SET DEFAULT nextval('public.email_attachment_id_seq'::regclass);


--
-- Name: email_blacklist_entry id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_blacklist_entry ALTER COLUMN id SET DEFAULT nextval('public.email_blacklist_entry_id_seq'::regclass);


--
-- Name: email_draft id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft ALTER COLUMN id SET DEFAULT nextval('public.email_draft_id_seq'::regclass);


--
-- Name: email_draft_attachment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft_attachment ALTER COLUMN id SET DEFAULT nextval('public.email_draft_attachment_id_seq'::regclass);


--
-- Name: email_signature id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_signature ALTER COLUMN id SET DEFAULT nextval('public.email_signature_id_seq'::regclass);


--
-- Name: email_signature_image id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_signature_image ALTER COLUMN id SET DEFAULT nextval('public.email_signature_image_id_seq'::regclass);


--
-- Name: email_text_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_text_template ALTER COLUMN id SET DEFAULT nextval('public.email_text_template_id_seq'::regclass);


--
-- Name: feiertag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feiertag ALTER COLUMN id SET DEFAULT nextval('public.feiertag_id_seq'::regclass);


--
-- Name: firma_kostenstelle id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_kostenstelle ALTER COLUMN id SET DEFAULT nextval('public.firma_kostenstelle_id_seq'::regclass);


--
-- Name: formular_template_assignment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_assignment ALTER COLUMN id SET DEFAULT nextval('public.formular_template_assignment_id_seq'::regclass);


--
-- Name: formular_template_textbaustein_default id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_textbaustein_default ALTER COLUMN id SET DEFAULT nextval('public.formular_template_textbaustein_default_id_seq'::regclass);


--
-- Name: frontend_user_profile id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile ALTER COLUMN id SET DEFAULT nextval('public.frontend_user_profile_id_seq'::regclass);


--
-- Name: gewerk id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gewerk ALTER COLUMN id SET DEFAULT nextval('public.gewerk_id_seq'::regclass);


--
-- Name: kalender_eintrag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag ALTER COLUMN id SET DEFAULT nextval('public.kalender_eintrag_id_seq'::regclass);


--
-- Name: kasse_einstellung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kasse_einstellung ALTER COLUMN id SET DEFAULT nextval('public.kasse_einstellung_id_seq'::regclass);


--
-- Name: kassenbuch_monatsabschluss id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kassenbuch_monatsabschluss ALTER COLUMN id SET DEFAULT nextval('public.kassenbuch_monatsabschluss_id_seq'::regclass);


--
-- Name: kassenzaehlung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kassenzaehlung ALTER COLUMN id SET DEFAULT nextval('public.kassenzaehlung_id_seq'::regclass);


--
-- Name: kategorie id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategorie ALTER COLUMN id SET DEFAULT nextval('public.kategorie_id_seq'::regclass);


--
-- Name: kontakt_rufnummer id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kontakt_rufnummer ALTER COLUMN id SET DEFAULT nextval('public.kontakt_rufnummer_id_seq'::regclass);


--
-- Name: kostenposition id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kostenposition ALTER COLUMN id SET DEFAULT nextval('public.kostenposition_id_seq'::regclass);


--
-- Name: krankenkasse id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.krankenkasse ALTER COLUMN id SET DEFAULT nextval('public.krankenkasse_id_seq'::regclass);


--
-- Name: kunde id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde ALTER COLUMN id SET DEFAULT nextval('public.kunde_id_seq'::regclass);


--
-- Name: kunde_notiz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde_notiz ALTER COLUMN id SET DEFAULT nextval('public.kunde_notiz_id_seq'::regclass);


--
-- Name: langzeitkrankmeldung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung ALTER COLUMN id SET DEFAULT nextval('public.langzeitkrankmeldung_id_seq'::regclass);


--
-- Name: langzeitkrankmeldung_phase id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung_phase ALTER COLUMN id SET DEFAULT nextval('public.langzeitkrankmeldung_phase_id_seq'::regclass);


--
-- Name: leistung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leistung ALTER COLUMN id SET DEFAULT nextval('public.leistung_id_seq'::regclass);


--
-- Name: lieferant_bild id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_bild ALTER COLUMN id SET DEFAULT nextval('public.lieferant_bild_id_seq'::regclass);


--
-- Name: lieferant_dokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument ALTER COLUMN id SET DEFAULT nextval('public.lieferant_dokument_id_seq'::regclass);


--
-- Name: lieferant_dokument_position id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_position ALTER COLUMN id SET DEFAULT nextval('public.lieferant_dokument_position_id_seq'::regclass);


--
-- Name: lieferant_dokument_projekt_anteil id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil ALTER COLUMN id SET DEFAULT nextval('public.lieferant_dokument_projekt_anteil_id_seq'::regclass);


--
-- Name: lieferant_notiz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_notiz ALTER COLUMN id SET DEFAULT nextval('public.lieferant_notiz_id_seq'::regclass);


--
-- Name: lieferant_reklamation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_reklamation ALTER COLUMN id SET DEFAULT nextval('public.lieferant_reklamation_id_seq'::regclass);


--
-- Name: lieferanten id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten ALTER COLUMN id SET DEFAULT nextval('public.lieferanten_id_seq'::regclass);


--
-- Name: lieferanten_artikel_preise id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_artikel_preise ALTER COLUMN id SET DEFAULT nextval('public.lieferanten_artikel_preise_id_seq'::regclass);


--
-- Name: lohnabrechnung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lohnabrechnung ALTER COLUMN id SET DEFAULT nextval('public.lohnabrechnung_id_seq'::regclass);


--
-- Name: materialkosten id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materialkosten ALTER COLUMN id SET DEFAULT nextval('public.materialkosten_id_seq'::regclass);


--
-- Name: miete_kostenstelle id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.miete_kostenstelle ALTER COLUMN id SET DEFAULT nextval('public.miete_kostenstelle_id_seq'::regclass);


--
-- Name: mietobjekt id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietobjekt ALTER COLUMN id SET DEFAULT nextval('public.mietobjekt_id_seq'::regclass);


--
-- Name: mietpartei id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietpartei ALTER COLUMN id SET DEFAULT nextval('public.mietpartei_id_seq'::regclass);


--
-- Name: mitarbeiter id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter ALTER COLUMN id SET DEFAULT nextval('public.mitarbeiter_id_seq'::regclass);


--
-- Name: mitarbeiter_dokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_dokument ALTER COLUMN id SET DEFAULT nextval('public.mitarbeiter_dokument_id_seq'::regclass);


--
-- Name: mitarbeiter_notiz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_notiz ALTER COLUMN id SET DEFAULT nextval('public.mitarbeiter_notiz_id_seq'::regclass);


--
-- Name: mitarbeiter_stundenlohn id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_stundenlohn ALTER COLUMN id SET DEFAULT nextval('public.mitarbeiter_stundenlohn_id_seq'::regclass);


--
-- Name: monats_saldo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monats_saldo ALTER COLUMN id SET DEFAULT nextval('public.monats_saldo_id_seq'::regclass);


--
-- Name: monatsabschluss_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monatsabschluss_audit ALTER COLUMN id SET DEFAULT nextval('public.monatsabschluss_audit_id_seq'::regclass);


--
-- Name: ooo_reply_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ooo_reply_log ALTER COLUMN id SET DEFAULT nextval('public.ooo_reply_log_id_seq'::regclass);


--
-- Name: out_of_office_schedule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.out_of_office_schedule ALTER COLUMN id SET DEFAULT nextval('public.out_of_office_schedule_id_seq'::regclass);


--
-- Name: produktkategorie id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produktkategorie ALTER COLUMN id SET DEFAULT nextval('public.produktkategorie_id_seq'::regclass);


--
-- Name: projekt id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt ALTER COLUMN id SET DEFAULT nextval('public.projekt_id_seq'::regclass);


--
-- Name: projekt_dokument id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument ALTER COLUMN id SET DEFAULT nextval('public.projekt_dokument_id_seq'::regclass);


--
-- Name: projekt_notiz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz ALTER COLUMN id SET DEFAULT nextval('public.projekt_notiz_id_seq'::regclass);


--
-- Name: projekt_notiz_bild id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz_bild ALTER COLUMN id SET DEFAULT nextval('public.projekt_notiz_bild_id_seq'::regclass);


--
-- Name: projekt_produktkategorie id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_produktkategorie ALTER COLUMN id SET DEFAULT nextval('public.projekt_produktkategorie_id_seq'::regclass);


--
-- Name: push_subscription id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscription ALTER COLUMN id SET DEFAULT nextval('public.push_subscription_id_seq'::regclass);


--
-- Name: raum id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raum ALTER COLUMN id SET DEFAULT nextval('public.raum_id_seq'::regclass);


--
-- Name: sachkonto id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sachkonto ALTER COLUMN id SET DEFAULT nextval('public.sachkonto_id_seq'::regclass);


--
-- Name: schnittbilder id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schnittbilder ALTER COLUMN id SET DEFAULT nextval('public.schnittbilder_id_seq'::regclass);


--
-- Name: spam_model_stats id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_model_stats ALTER COLUMN id SET DEFAULT nextval('public.spam_model_stats_id_seq'::regclass);


--
-- Name: spam_token_count id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_token_count ALTER COLUMN id SET DEFAULT nextval('public.spam_token_count_id_seq'::regclass);


--
-- Name: sprachnachricht id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht ALTER COLUMN id SET DEFAULT nextval('public.sprachnachricht_id_seq'::regclass);


--
-- Name: steuerberater_ansprechpartner id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_ansprechpartner ALTER COLUMN id SET DEFAULT nextval('public.steuerberater_ansprechpartner_id_seq'::regclass);


--
-- Name: steuerberater_kontakt id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_kontakt ALTER COLUMN id SET DEFAULT nextval('public.steuerberater_kontakt_id_seq'::regclass);


--
-- Name: sv_satz id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sv_satz ALTER COLUMN id SET DEFAULT nextval('public.sv_satz_id_seq'::regclass);


--
-- Name: telefon_anruf id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telefon_anruf ALTER COLUMN id SET DEFAULT nextval('public.telefon_anruf_id_seq'::regclass);


--
-- Name: textbaustein id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.textbaustein ALTER COLUMN id SET DEFAULT nextval('public.textbaustein_id_seq'::regclass);


--
-- Name: urlaubsantrag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.urlaubsantrag ALTER COLUMN id SET DEFAULT nextval('public.urlaubsantrag_id_seq'::regclass);


--
-- Name: verbrauchsgegenstand id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verbrauchsgegenstand ALTER COLUMN id SET DEFAULT nextval('public.verbrauchsgegenstand_id_seq'::regclass);


--
-- Name: verteilungsschluessel id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel ALTER COLUMN id SET DEFAULT nextval('public.verteilungsschluessel_id_seq'::regclass);


--
-- Name: verteilungsschluessel_eintrag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel_eintrag ALTER COLUMN id SET DEFAULT nextval('public.verteilungsschluessel_eintrag_id_seq'::regclass);


--
-- Name: website_analytics_snapshot id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_analytics_snapshot ALTER COLUMN id SET DEFAULT nextval('public.website_analytics_snapshot_id_seq'::regclass);


--
-- Name: werkstoff id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.werkstoff ALTER COLUMN id SET DEFAULT nextval('public.werkstoff_id_seq'::regclass);


--
-- Name: zaehlerstand id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zaehlerstand ALTER COLUMN id SET DEFAULT nextval('public.zaehlerstand_id_seq'::regclass);


--
-- Name: zahlungsart id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zahlungsart ALTER COLUMN id SET DEFAULT nextval('public.zahlungsart_id_seq'::regclass);


--
-- Name: zeitbuchung id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung ALTER COLUMN id SET DEFAULT nextval('public.zeitbuchung_id_seq'::regclass);


--
-- Name: zeitbuchung_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung_audit ALTER COLUMN id SET DEFAULT nextval('public.zeitbuchung_audit_id_seq'::regclass);


--
-- Name: zeitkontenmodell id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkontenmodell ALTER COLUMN id SET DEFAULT nextval('public.zeitkontenmodell_id_seq'::regclass);


--
-- Name: zeitkonto_korrektur id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur ALTER COLUMN id SET DEFAULT nextval('public.zeitkonto_korrektur_id_seq'::regclass);


--
-- Name: zeitkonto_korrektur_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur_audit ALTER COLUMN id SET DEFAULT nextval('public.zeitkonto_korrektur_audit_id_seq'::regclass);


--
-- Name: zeitkonto_pause id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_pause ALTER COLUMN id SET DEFAULT nextval('public.zeitkonto_pause_id_seq'::regclass);


--
-- Name: zeitkonto_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_version ALTER COLUMN id SET DEFAULT nextval('public.zeitkonto_version_id_seq'::regclass);


--
-- Data for Name: abteilung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: abteilung_dokument_berechtigung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: abwesenheit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: aenderungsgrund_katalog; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage_dokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage_geschaeftsdokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage_kunden_emails; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage_notiz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: anfrage_notiz_bild; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: arbeitsgang; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: arbeitsgang_stundensatz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: arbeitszeitart; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: artikel; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 1, NULL, NULL, 3, 'AL57-BL-0.75', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '0.75 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-0.75 0.75 0,75    0.75  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 2, NULL, NULL, 3, 'AL57-BL-1', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '1 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-1 1 1    1  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 3, NULL, NULL, 3, 'AL57-BL-1.25', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '1.25 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-1.25 1.25 1,25    1.25  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 4, NULL, NULL, 3, 'AL57-BL-1.5', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '1.5 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-1.5 1.5 1,5    1.5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 5, NULL, NULL, 3, 'AL57-BL-2', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '2 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-2 2 2    2  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 6, NULL, NULL, 3, 'AL57-BL-2.5', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '2.5 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-2.5 2.5 2,5    2.5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 7, NULL, NULL, 3, 'AL57-BL-3', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '3 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-3 3 3    3  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 8, NULL, NULL, 3, 'AL57-BL-4', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '4 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-4 4 4    4  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 9, NULL, NULL, 3, 'AL57-BL-5', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '5 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-5 5 5    5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 10, NULL, NULL, 3, 'AL57-BL-6', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '6 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-6 6 6    6  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 11, NULL, NULL, 3, 'AL57-BL-8', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '8 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-8 8 8    8  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 12, NULL, NULL, 3, 'AL57-BL-10', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '10 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-10 10 10    10  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 13, NULL, NULL, 3, 'AL57-BL-12', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '12 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-12 12 12    12  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 14, NULL, NULL, 3, 'AL57-BL-15', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '15 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-15 15 15    15  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 15, NULL, NULL, 3, 'AL57-BL-20', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '20 mm', NULL, 'BLECH', 'blech glattblech tafel platte al57-bl-20 20 20    20  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 16, NULL, NULL, 4, 'DXZ-BL-0.75', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '0.75 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-0.75 0.75 0,75    0.75  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 17, NULL, NULL, 4, 'DXZ-BL-1', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '1 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-1 1 1    1  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 18, NULL, NULL, 4, 'DXZ-BL-1.25', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '1.25 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-1.25 1.25 1,25    1.25  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 19, NULL, NULL, 4, 'DXZ-BL-1.5', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '1.5 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-1.5 1.5 1,5    1.5  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 20, NULL, NULL, 4, 'DXZ-BL-2', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '2 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-2 2 2    2  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 21, NULL, NULL, 4, 'DXZ-BL-2.5', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '2.5 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-2.5 2.5 2,5    2.5  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (8, NULL, true, NULL, NULL, 22, NULL, NULL, 4, 'DXZ-BL-3', 'EN 10346', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 10346', '3 mm', NULL, 'BLECH', 'blech glattblech tafel platte dxz-bl-3 3 3    3  dx51d+z stahlblech verzinkt (sendzimir) en 10346 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (9, NULL, true, NULL, NULL, 23, NULL, NULL, 3, 'AL57-RB-2', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '2 mm', NULL, 'RIFFELBLECH', 'riffelblech traenenblech blech rutschhemmend al57-rb-2 2 2    2  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (9, NULL, true, NULL, NULL, 24, NULL, NULL, 3, 'AL57-RB-3', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '3 mm', NULL, 'RIFFELBLECH', 'riffelblech traenenblech blech rutschhemmend al57-rb-3 3 3    3  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (9, NULL, true, NULL, NULL, 25, NULL, NULL, 3, 'AL57-RB-4', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '4 mm', NULL, 'RIFFELBLECH', 'riffelblech traenenblech blech rutschhemmend al57-rb-4 4 4    4  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (9, NULL, true, NULL, NULL, 26, NULL, NULL, 3, 'AL57-RB-5', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '5 mm', NULL, 'RIFFELBLECH', 'riffelblech traenenblech blech rutschhemmend al57-rb-5 5 5    5  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');
INSERT INTO public.artikel (kategorie_id, pulverbeschichtungsgeeignet, system_stammdaten, verkaufsaufschlag_prozent, verzinkungsgeeignet, id, verpackungseinheit, version, werkstoff_id, artikelnummer, massnorm, werkstoffnorm, beschreibung, fertigungszustand, herstellverfahren, hicad_name, kurzbeschreibung, preiseinheit, produktlinie, produktname, produkttext, profilform, suchtext, verrechnungseinheit) VALUES (9, NULL, true, NULL, NULL, 27, NULL, NULL, 3, 'AL57-RB-6', 'EN 485-2', NULL, NULL, 'KALTGEWALZT', 'GEWALZT', NULL, NULL, NULL, 'EN 485-2', '6 mm', NULL, 'RIFFELBLECH', 'riffelblech traenenblech blech rutschhemmend al57-rb-6 6 6    6  en aw-5754 aluminium en aw-5754 (blech) en 485-2 gewalzt kaltgewalzt', 'QUADRATMETER');


--
-- Data for Name: artikel_dokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: artikel_hilfsstoffe; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: artikel_in_projekt; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: artikel_werkstoffe; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 1.9950, NULL, NULL, NULL, 0.75, 1);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 2.6600, NULL, NULL, NULL, 1.00, 2);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 3.3250, NULL, NULL, NULL, 1.25, 3);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 3.9900, NULL, NULL, NULL, 1.50, 4);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 5.3200, NULL, NULL, NULL, 2.00, 5);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 6.6500, NULL, NULL, NULL, 2.50, 6);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 7.9800, NULL, NULL, NULL, 3.00, 7);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 10.6400, NULL, NULL, NULL, 4.00, 8);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 13.3000, NULL, NULL, NULL, 5.00, 9);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 15.9600, NULL, NULL, NULL, 6.00, 10);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 21.2800, NULL, NULL, NULL, 8.00, 11);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 26.6000, NULL, NULL, NULL, 10.00, 12);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 31.9200, NULL, NULL, NULL, 12.00, 13);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 39.9000, NULL, NULL, NULL, 15.00, 14);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 53.2000, NULL, NULL, NULL, 20.00, 15);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 5.8875, NULL, NULL, NULL, 0.75, 16);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 7.8500, NULL, NULL, NULL, 1.00, 17);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 9.8125, NULL, NULL, NULL, 1.25, 18);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 11.7750, NULL, NULL, NULL, 1.50, 19);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 15.7000, NULL, NULL, NULL, 2.00, 20);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 19.6250, NULL, NULL, NULL, 2.50, 21);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 23.5500, NULL, NULL, NULL, 3.00, 22);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 6.0914, NULL, NULL, NULL, 2.00, 23);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 8.7514, NULL, NULL, NULL, 3.00, 24);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 11.4114, NULL, NULL, NULL, 4.00, 25);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 14.0714, NULL, NULL, NULL, 5.00, 26);
INSERT INTO public.artikel_werkstoffe (breite, durchmesser, flanschdicke, geschliffen, hoehe, mantelflaeche, masse_pro_meter, masse_pro_qm, querschnittsflaeche, standardlaenge_mm, stegdicke, wandstaerke, id) VALUES (NULL, NULL, NULL, false, NULL, 2.0000, NULL, 16.7314, NULL, NULL, NULL, 6.00, 27);


--
-- Data for Name: audit_chain_state; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.audit_chain_state (id, last_chain_index, updated_at, last_entry_hash) VALUES (1, -1, '2026-10-10 14:45:38.165197', NULL);


--
-- Data for Name: ausgangs_geschaeftsdokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ausgangs_geschaeftsdokument_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ausgangs_geschaeftsdokument_counter; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: beleg; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: beleg_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: beleg_audit_chain_state; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.beleg_audit_chain_state (id, last_chain_index, last_laufende_nummer, updated_at, last_entry_hash) VALUES (1, -1, 0, '2026-10-10 14:45:46.282469', NULL);


--
-- Data for Name: beleg_kostenstellen_anteil; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: beleg_position; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: bwa_position; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: bwa_upload; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: datensatz_lock; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: datev_konfiguration; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.datev_konfiguration (mandanten_nr, berater_nr, aenderungszaehler, id, version, ziel, zuordnungen_json) VALUES ('', '', 0, 1, 0, 'LODAS', '[]');


--
-- Data for Name: datev_personalnummer; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dokument_freigabe; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dokumentnummer_counter; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_absender; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_attachment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_blacklist_entry; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_draft; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_draft_attachment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_signature; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.email_signature (is_system_default, created_at, id, updated_at, name, html) VALUES (true, '2026-10-10 14:45:54.260156', 1, '2026-10-10 14:45:54.260156', 'System (automatische E-Mails)', '<div class="email-signature" data-system-placeholder="1" style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#888;border:1px dashed #cbd5e1;background:#f8fafc;padding:12px;border-radius:6px;"><p style="margin:0 0 6px 0;font-weight:600;color:#475569;">Hier kann Ihre System-Signatur eingetragen werden.</p><p style="margin:0;">Diese Signatur wird an alle automatisch versendeten E-Mails (Auftragsbest&auml;tigungen, Mahnungen, ...) angeh&auml;ngt. Bitte im Bereich „E-Mail-Signaturen" anpassen.</p></div>');


--
-- Data for Name: email_signature_image; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_text_template; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.371859+00', 1, '2026-10-10 14:45:36.371859+00', 'DOKUMENT', 'RECHNUNG', 'Rechnung', 'Rechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Rechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span> auf das in der Rechnung angegebene Konto.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.373728+00', 2, '2026-10-10 14:45:36.373728+00', 'DOKUMENT', 'TEILRECHNUNG', 'Teilrechnung', 'Teilrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen eine Teilrechnung für unsere bereits erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.374933+00', 3, '2026-10-10 14:45:36.374933+00', 'DOKUMENT', 'SCHLUSSRECHNUNG', 'Schlussrechnung', 'Schlussrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Schlussrechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p>Wir würden uns sehr über eine Bewertung freuen: {{REVIEW_LINK}}</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.375795+00', 4, '2026-10-10 14:45:36.375795+00', 'DOKUMENT', 'ABSCHLAGSRECHNUNG', 'Abschlagsrechnung', 'Abschlagsrechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen eine Abschlagsrechnung gemäß unserer Vereinbarung. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Gesamtbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p><p>Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span>.<br><strong>Bitte geben Sie im Verwendungszweck die Projekt- und Rechnungsnummer an.</strong></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.37667+00', 5, '2026-10-10 14:45:37.736683+00', 'MAHNWESEN', 'ERSTE_MAHNUNG', '1. Mahnung', '1. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>leider haben wir festgestellt, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Bitte überweisen Sie den ausstehenden Betrag umgehend, um zusätzliche Mahngebühren zu vermeiden.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.377371+00', 6, '2026-10-10 14:45:36.377371+00', 'DOKUMENT', 'ANGEBOT', 'Anfrage / Angebot', 'Anfrage: (BV: {{BAUVORHABEN}}) Anfragesnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>im Anhang finden Sie das besprochene Angebot.<br>Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.</p><p>Bei Auftragserteilung wird von uns eine 3D-Zeichnung mit genauen Maßen erstellt.<br>Nach Freigabe der Zeichnung gehen wir in die Produktion.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Anfragesnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.378007+00', 7, '2026-10-10 14:45:36.378007+00', 'DOKUMENT', 'AUFTRAGSBESTAETIGUNG', 'Auftragsbestätigung', 'Auftragsbestätigung: (BV: {{BAUVORHABEN}}) Auftragsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei sende ich Ihnen die Auftragsbestätigung. Die detaillierte Auftragsbestätigung finden Sie als PDF-Datei im Anhang dieser E-Mail.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Auftragsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Auftragssumme:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:36.378518+00', 8, '2026-10-10 14:45:36.378518+00', 'DOKUMENT', 'ZEICHNUNG', 'Zeichnung / Entwurf', 'Kundenzeichnung BV: ({{BAUVORHABEN}})', '<p>{{ANREDE}},</p><p>anbei finden Sie die PDF mit dem ersten Entwurf Ihres Bauprojekts.<br>Bitte nehmen Sie sich etwas Zeit, um das Design sorgfältig zu überprüfen.<br>Sollten Sie weitere Änderungswünsche haben oder Fragen auftauchen, stehe ich Ihnen gerne zur Verfügung.</p><p>Wir möchten Sie darauf hinweisen, dass größere Zeichnungsänderungen, die gravierend vom ursprünglichen Angebot abweichen, aufgrund des damit verbundenen Zeitaufwands zusätzliche Kosten verursachen können. Wir bitten um Ihr Verständnis dafür.</p><p>Falls dies im Angebot so vereinbart war, wird nach Abschluss der Planung eine Abschlagsrechnung erstellt.<br>Bei Fragen oder weiteren Anliegen stehe ich Ihnen jederzeit zur Verfügung.</p><p>Vielen Dank für Ihre Zusammenarbeit und Ihr Verständnis.</p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:37.73856+00', 9, '2026-10-10 14:45:37.73856+00', 'MAHNWESEN', 'ZAHLUNGSERINNERUNG', 'Zahlungserinnerung', 'Zahlungserinnerung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>vermutlich ist es Ihrer Aufmerksamkeit entgangen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} noch nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Bitte überweisen Sie den ausstehenden Betrag in den nächsten Tagen. Sollte sich Ihre Zahlung mit dieser Erinnerung überschnitten haben, betrachten Sie diese E-Mail bitte als gegenstandslos.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:37.740721+00', 11, '2026-10-10 14:45:37.740721+00', 'MAHNWESEN', 'ZWEITE_MAHNUNG', '2. Mahnung', '2. Mahnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>leider mussten wir feststellen, dass die Rechnung mit der Nummer {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}} auch nach unserer 1. Mahnung noch immer nicht beglichen wurde.</p><p>Der Betrag in Höhe von <strong>{{BETRAG}}</strong> war bereits am <strong>{{FAELLIGKEITSDATUM}}</strong> fällig.</p><p>Wir fordern Sie hiermit letztmalig auf, den ausstehenden Betrag innerhalb von 7 Tagen zu überweisen. Andernfalls sehen wir uns gezwungen, die Forderung an ein Inkassobüro zu übergeben oder gerichtliche Schritte einzuleiten. Die dadurch entstehenden Kosten gehen zu Ihren Lasten.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Rechnungsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Fälligkeitsdatum:</strong> <span style="color:#C00000">{{FAELLIGKEITSDATUM}}</span><br><strong>Offener Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:37.741732+00', 12, '2026-10-10 14:45:37.741732+00', 'DOKUMENT', 'STORNORECHNUNG', 'Stornorechnung', 'Stornorechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei erhalten Sie die Stornorechnung zur Rechnung {{DOKUMENTNUMMER}} für das Bauvorhaben {{BAUVORHABEN}}.</p><p>Mit dieser Stornorechnung wird die ursprüngliche Rechnung in voller Höhe storniert. Bitte ersetzen Sie die ursprüngliche Rechnung in Ihren Unterlagen durch die anliegende Stornorechnung.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Stornorechnung-Nr.:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Stornierter Betrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:37.742564+00', 13, '2026-10-10 14:45:37.742564+00', 'DOKUMENT', 'GUTSCHRIFT', 'Gutschrift', 'Gutschrift: (BV: {{BAUVORHABEN}}) Gutschrift-Nr.: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>anbei erhalten Sie die Gutschrift für das Bauvorhaben {{BAUVORHABEN}}.</p><p>Den Gutschriftsbetrag werden wir in den nächsten Tagen auf Ihr Konto überweisen bzw. mit der nächsten Rechnung verrechnen.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Projektnummer:</strong> <span style="color:#C00000">{{PROJEKTNUMMER}}</span><br><strong>Gutschrift-Nr.:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span><br><strong>Rechnungsdatum:</strong> <span style="color:#C00000">{{RECHNUNGSDATUM}}</span><br><strong>Gutschriftsbetrag:</strong> <span style="color:#C00000">{{BETRAG}}</span></p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:40.8465+00', 14, '2026-10-10 14:45:40.8465+00', 'WEBSITE', 'WEBSITE_ANFRAGE_BESTAETIGUNG', 'Webseite — Anfragebestätigung', 'Wir haben Ihre Anfrage erhalten — BV: {{BAUVORHABEN}}', '<p>{{ANREDE}},</p><p>vielen Dank für Ihre Anfrage über unsere Webseite! Wir haben Ihre Nachricht erhalten und melden uns innerhalb der nächsten 1–2 Werktage persönlich bei Ihnen.</p><p><strong>Ihre Angaben:</strong><br>Bauvorhaben: <span style="color:#C00000">{{BAUVORHABEN}}</span><br>Anfrage-Datum: <span style="color:#C00000">{{ANFRAGE_DATUM}}</span><br>Anfrage-Nr.: <span style="color:#C00000">{{ANFRAGENUMMER}}</span></p><p><strong>Ihre Nachricht an uns:</strong></p><p style="white-space:pre-wrap;color:#475569;border-left:3px solid #e5e7eb;padding:6px 12px;">{{NACHRICHT}}</p><p>Sollten sich Details an Ihrem Projekt geändert haben, antworten Sie einfach auf diese E-Mail — wir ergänzen Ihre Anfrage dann gerne.</p>');
INSERT INTO public.email_text_template (aktiv, created_at, id, updated_at, kategorie, dokument_typ, name, subject_template, html_body) VALUES (true, '2026-10-10 14:45:43.295378+00', 15, '2026-10-10 14:45:43.295378+00', 'DOKUMENT', 'NACHTRAGSANGEBOT', 'Nachtragsangebot', 'Nachtragsangebot: (BV: {{BAUVORHABEN}}) Angebotsnummer: {{DOKUMENTNUMMER}}', '<p>{{ANREDE}} {{KUNDENNAME}},</p><p>im Anhang finden Sie unser Nachtragsangebot zu dem laufenden Projekt.<br>Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.</p><p><strong>Bauvorhaben:</strong> <span style="color:#C00000">{{BAUVORHABEN}}</span><br><strong>Angebotsnummer:</strong> <span style="color:#C00000">{{DOKUMENTNUMMER}}</span></p>');


--
-- Data for Name: entity_last_accessed; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: feiertag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: firma_kostenstelle; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: firmeninformation; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: formular_template_assignment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: formular_template_textbaustein_default; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: frontend_user_profile; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: frontend_user_profile_role; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: gewerk; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 5.46, 1, 'Richtwert - genauer Beitrag kommt aus dem Beitragsbescheid.', 'BG BAU', 'Bauhauptgewerbe (Hochbau, Maurer, Beton)');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 3.30, 2, NULL, 'BG BAU', 'Ausbau (Trockenbau, Putz, Stuck)');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 7.70, 3, NULL, 'BG BAU', 'Dachdecker');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 3.30, 4, NULL, 'BG BAU', 'Maler und Lackierer');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 6.80, 5, NULL, 'BG BAU', 'Geruestbau');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 5.50, 6, NULL, 'BG BAU', 'Fliesen-, Platten- und Mosaikleger');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 1.13, 7, NULL, 'BGHM', 'Tischler / Schreiner');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 1.85, 8, NULL, 'BGHM', 'Metallbau / Schlosserei');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 1.85, 9, NULL, 'BGHM', 'Kfz-Werkstatt');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 1.10, 10, NULL, 'BG ETEM', 'Elektroinstallation');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 2.10, 11, NULL, 'BG ETEM', 'Sanitaer / Heizung / Klima (SHK)');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 3.40, 12, NULL, 'SVLFG', 'Garten- und Landschaftsbau');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 5.46, 13, NULL, 'BG BAU', 'Gebaeudereinigung');
INSERT INTO public.gewerk (aktiv, bg_satz_prozent, id, bemerkung, bg_name, name) VALUES (true, 3.00, 14, 'Platzhalter - bitte BG und Satz manuell eintragen.', 'Individuell', 'Andere / Sonstige');


--
-- Data for Name: kalender_eintrag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kalender_eintrag_teilnehmer; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kasse_einstellung; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.kasse_einstellung (ehegattengehalt_aktiv, ehegattengehalt_betrag, ehegattengehalt_tag, mindestbestand, wirtschaftsjahr_beginn_monat, datev_mandantennummer, datev_beraternummer, letzte_buchung_jahrmonat, aktualisiert_am, bankkonto_nummer, id, kassenkonto_nummer, privateinlage_sachkonto_id, ehegattengehalt_empfaenger_name) VALUES (false, NULL, NULL, 0.00, 1, NULL, NULL, NULL, '2026-10-10 14:45:42', '1200', 1, '1000', NULL, NULL);


--
-- Data for Name: kassenbuch_monatsabschluss; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kassenzaehlung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kategorie; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.kategorie (id, parent_kategorie_id, beschreibung) VALUES (7, NULL, 'Blech');
INSERT INTO public.kategorie (id, parent_kategorie_id, beschreibung) VALUES (8, 7, 'Glattblech');
INSERT INTO public.kategorie (id, parent_kategorie_id, beschreibung) VALUES (9, 7, 'Riffelblech');


--
-- Data for Name: kategorie_rollen; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kontakt_rufnummer; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kostenposition; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: krankenkasse; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.45, 1, 'TK', NULL, 'Techniker Krankenkasse');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 2, 'AOK-BY', NULL, 'AOK Bayern');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 3, 'AOK-NW', NULL, 'AOK NordWest');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 4, 'AOK-BW', NULL, 'AOK Baden-Wuerttemberg');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 3.49, 5, 'BARMER', NULL, 'Barmer');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 6, 'DAK', NULL, 'DAK-Gesundheit');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 7, 'IKK', NULL, 'IKK classic');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 8, 'KBS', NULL, 'Knappschaft');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.40, 9, 'BKK-VBU', NULL, 'BKK VBU');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.70, 10, 'HEK', NULL, 'HEK - Hanseatische Krankenkasse');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 1.84, 11, 'HKK', NULL, 'hkk Krankenkasse');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 2.40, 12, 'MHPLUS', NULL, 'mhplus Krankenkasse');
INSERT INTO public.krankenkasse (aktiv, gueltig_ab, zusatzbeitrag_prozent, id, kuerzel, bemerkung, name) VALUES (true, '2026-01-01', 1.99, 13, 'BIG', NULL, 'BIG direkt gesund');


--
-- Data for Name: kunde; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kunde_notiz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kunden_emails; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: kunden_zaehler; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.kunden_zaehler (id, naechste_nummer) VALUES (1, 1000);


--
-- Data for Name: langzeitkrankmeldung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: langzeitkrankmeldung_phase; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: leistung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_bild; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_dokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_dokument_position; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_dokument_projekt_anteil; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_dokument_verknuepfung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_dokument_verknuepfung_gesperrt; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_geschaeftsdokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_notiz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferant_reklamation; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferanten; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferanten_artikel_preise; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferanten_emails; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lieferanten_rollen; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lohnabrechnung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: materialkosten; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: miete_kostenstelle; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mietobjekt; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mietpartei; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mitarbeiter; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.mitarbeiter (aktiv, eintrittsdatum, fuehrt_zeitkonto, geburtstag, geldwert_vorteil_monat, ist_geschaeftsfuehrer, jahres_urlaub, kalkulatorischer_lohn_monat, kinderlos, resturlaub_vorjahr, stundenlohn, urlaubs_korrektur, id, krankenkasse_id, version, art, beschaeftigungsart, email, festnetz, login_token, nachname, ort, plz, qualifikation, strasse, telefon, vorname) VALUES (true, NULL, false, NULL, NULL, false, NULL, NULL, false, NULL, NULL, NULL, 1, NULL, 0, 'SYSTEM', 'REGULAER', NULL, NULL, '__SYSTEM_FUNNEL__', 'Webseite', NULL, NULL, NULL, NULL, NULL, 'System');


--
-- Data for Name: mitarbeiter_abteilung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mitarbeiter_dokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mitarbeiter_notiz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mitarbeiter_stundenlohn; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: monats_saldo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: monatsabschluss_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ooo_reply_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: out_of_office_schedule; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: produktkategorie; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_dokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_geschaeftsdokument; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_kunden_emails; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_notiz; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_notiz_bild; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: projekt_produktkategorie; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: push_subscription; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: raum; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: sachkonto; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 10, 1, 'AUFWAND', '3400', 'Materialeinkauf', 'Material, Baustoffe, Rohstoffe');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 20, 2, 'AUFWAND', '4400', 'Werkzeug & Kleingeraete', 'Werkzeug, Kleingeraete (sofort abschreibbar)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 30, 3, 'AUFWAND', '4530', 'Fahrzeugkosten', 'Kraftstoff, Wartung, Reparaturen, KFZ-Versicherung');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 40, 4, 'AUFWAND', '4910', 'Telefon & Internet', 'Mobilfunk, Festnetz, Internet');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 50, 5, 'AUFWAND', '4930', 'Buerobedarf', 'Papier, Toner, Stifte, Software');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 60, 6, 'AUFWAND', '4940', 'Reinigung', 'Putzmittel, Reinigungsdienst');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 70, 7, 'AUFWAND', '4945', 'Verpflegung & Bewirtung', 'Geschaeftsessen, Verpflegung Mitarbeiter');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 80, 8, 'AUFWAND', '4948', 'Reisekosten', 'Hotel, Bahn, Spesen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 90, 9, 'AUFWAND', '4380', 'Versicherungen', 'Betriebsversicherungen (ohne KFZ)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 100, 10, 'AUFWAND', '4360', 'Werbung & Marketing', 'Anzeigen, Website, Visitenkarten');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 200, 11, 'AUFWAND', '4980', 'Sonstiger Aufwand', 'Diverse betriebliche Aufwendungen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 300, 12, 'ERTRAG', '8400', 'Erloese 19%', 'Steuerpflichtige Erloese 19% (Bar/Karte/Bank)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 310, 13, 'ERTRAG', '8300', 'Erloese 7%', 'Steuerpflichtige Erloese 7%');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 400, 14, 'PRIVAT', '1800', 'Privatentnahme', 'Bar-Entnahme durch Inhaber');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 410, 15, 'PRIVAT', '1810', 'Privateinlage', 'Einlage durch Inhaber');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 500, 16, 'NEUTRAL', '1200', 'Bank-Kassen-Umbuchung', 'Bar abgehoben/eingezahlt; keine GuV-Wirkung');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 510, 17, 'NEUTRAL', '1700', 'Durchlaufende Posten', 'Treuhand, Kautionen, durchlaufende Auslagen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 11, 18, 'AUFWAND', '3100', 'Fremdleistungen / Subunternehmer', 'Bezahlte Rechnungen von Sub-Handwerkern und Dienstleistern');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 12, 19, 'AUFWAND', '3300', 'Wareneingang 19%', 'Handelswaren / Material zum Weiterverkauf (19% VSt)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 13, 20, 'AUFWAND', '3735', 'Skonti Aufwand', 'Gewaehrte Skonti / Boni an Kunden');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 21, 21, 'AUFWAND', '4120', 'Loehne & Gehaelter', 'Bruttoloehne und Gehaelter der Mitarbeiter');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 22, 22, 'AUFWAND', '4130', 'Sozialabgaben (AG-Anteil)', 'Arbeitgeberanteil zur Sozialversicherung');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 23, 23, 'AUFWAND', '4140', 'Berufsgenossenschaft', 'Beitraege zur BG (BG BAU, BG ETEM, ...)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 24, 24, 'AUFWAND', '4150', 'Aushilfsloehne', 'Minijobs, Aushilfen, kurzfristig Beschaeftigte');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 25, 25, 'AUFWAND', '4665', 'Berufskleidung', 'Arbeitskleidung, Schutzkleidung, Sicherheitsschuhe');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 26, 26, 'AUFWAND', '4946', 'Fortbildung & Schulung', 'Lehrgaenge, Meisterkurse, Sicherheitsschulungen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 31, 27, 'AUFWAND', '4210', 'Miete & Pacht (Geschaeft)', 'Miete fuer Werkstatt, Lager, Buero');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 32, 28, 'AUFWAND', '4240', 'Strom, Gas, Wasser', 'Energie- und Wasserkosten Betriebsstaette');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 33, 29, 'AUFWAND', '4250', 'Instandhaltung Gebaeude', 'Reparaturen am Geschaeftsgebaeude');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 71, 30, 'AUFWAND', '4650', 'Bewirtung geschaeftlich (70%)', 'Bewirtungsbelege mit Kundenbezug, 70% abzugsfaehig');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 72, 31, 'AUFWAND', '4630', 'Geschenke abzugsfaehig', 'Kundengeschenke bis 50 EUR netto');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 73, 32, 'AUFWAND', '4635', 'Geschenke nicht abzugsfaehig', 'Geschenke ueber 50 EUR netto');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 41, 33, 'AUFWAND', '4920', 'Porto', 'Briefporto, Paketversand');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 42, 34, 'AUFWAND', '4925', 'Software & IT-Abos', 'Cloud-Software, Lizenzen, Office-Abos');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 91, 35, 'AUFWAND', '4955', 'Buchfuehrungs- & Steuerberatung', 'Honorare Steuerberater, Buchfuehrungsservice');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 92, 36, 'AUFWAND', '4957', 'Rechts- & Beratungskosten', 'Anwaltskosten, Unternehmensberatung');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 93, 37, 'AUFWAND', '4390', 'Beitraege IHK / HWK / Innung', 'Pflichtbeitraege Kammer, Innung, Verbaende');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 101, 38, 'AUFWAND', '4970', 'Bankgebuehren & Kontofuehrung', 'Kontofuehrung, Kartengebuehren, Auslandsspesen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 102, 39, 'AUFWAND', '4975', 'Zinsaufwand', 'Zinsen fuer Betriebskredite, Kontokorrent');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 110, 40, 'AUFWAND', '4830', 'Abschreibung Anlagen (AfA)', 'Planmaessige Abschreibung Sachanlagen');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 111, 41, 'AUFWAND', '4855', 'Abschreibung GWG', 'Sofortabschreibung geringwertiger Wirtschaftsgueter (bis 800 EUR)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 320, 42, 'ERTRAG', '8338', 'Erloese steuerfrei (Reverse Charge)', 'Bauleistungen an andere Unternehmer (§ 13b UStG)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 330, 43, 'ERTRAG', '8125', 'Erloese steuerfrei innergem.', 'Innergemeinschaftliche Lieferungen (EU)');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 340, 44, 'ERTRAG', '8736', 'Skontoertraege', 'Erhaltene Skonti von Lieferanten');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 350, 45, 'ERTRAG', '8100', 'Mieteinnahmen', 'Vermietung von Raeumen / Gegenstaenden');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 420, 46, 'PRIVAT', '1820', 'Privatsteuer (Einkommensteuer)', 'Ueberwiesene Einkommensteuer-Vorauszahlung an FA');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 430, 47, 'PRIVAT', '1830', 'Privatanteil KFZ', 'Private Nutzung Firmenfahrzeug');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 440, 48, 'PRIVAT', '1840', 'Privatanteil Telefon', 'Privatnutzungsanteil Festnetz / Mobilfunk');
INSERT INTO public.sachkonto (aktiv, sortierung, id, konto_typ, nummer, bezeichnung, beschreibung) VALUES (true, 520, 49, 'NEUTRAL', '1600', 'Geldtransit', 'Geld unterwegs zwischen Kasse / Bank (Verrechnung)');


--
-- Data for Name: schnittbilder; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: seen_sender_domain; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: spam_model_stats; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.spam_model_stats (id, stat_value, stat_key) VALUES (1, 0, 'total_spam');
INSERT INTO public.spam_model_stats (id, stat_value, stat_key) VALUES (2, 0, 'total_ham');


--
-- Data for Name: spam_token_count; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: sprachnachricht; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: steuerberater_ansprechpartner; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: steuerberater_kontakt; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: steuerberater_kontakt_emails; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: sv_satz; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 14.60, 1, 'Allgemeiner Beitragssatz Krankenversicherung (mit Krankengeldanspruch).', 'KV_GESAMT');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 3.40, 2, 'Pflegeversicherung. Wird i. d. R. halbe/halbe getragen (Sachsen-Sonderregel ausgenommen).', 'PV_GESAMT');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 0.60, 3, 'Zuschlag fuer kinderlose Arbeitnehmer ab 23 Jahren - traegt allein der Arbeitnehmer.', 'PV_KINDERLOS_AN_ZUSCHLAG');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 18.60, 4, 'Rentenversicherung. Halbe/halbe.', 'RV_GESAMT');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 2.60, 5, 'Arbeitslosenversicherung. Halbe/halbe.', 'AV_GESAMT');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 13.00, 6, 'Minijob-Pauschale Krankenversicherung (Arbeitgeber, gewerblich).', 'MINIJOB_AG_KV');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 15.00, 7, 'Minijob-Pauschale Rentenversicherung (Arbeitgeber, gewerblich).', 'MINIJOB_AG_RV');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 2.00, 8, 'Pauschalsteuer Minijob (Arbeitgeber, optional - alternativ individuelle Lohnsteuer).', 'MINIJOB_AG_PAUSCHALSTEUER');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 1.10, 9, 'Umlage U1 (Lohnfortzahlung im Krankheitsfall) - kassenindividuell, hier Default-Wert.', 'U1_UMLAGE');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 0.24, 10, 'Umlage U2 (Mutterschaft) - kassenindividuell, hier Default-Wert.', 'U2_UMLAGE');
INSERT INTO public.sv_satz (gueltig_ab, prozent, id, beschreibung, satz_typ) VALUES ('2026-01-01', 0.06, 11, 'Insolvenzgeldumlage - traegt allein der Arbeitgeber.', 'INSOLVENZGELDUMLAGE');


--
-- Data for Name: system_setting; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.dokumente.host', 'Posteingangs-Server des Dokument-Postfachs (leer = derselbe wie beim Versand)', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.dokumente.port', 'IMAP Port des Dokument-Postfachs (993 = SSL)', '993');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.host', 'IMAP Mail-Server Hostname', 'secureimap.t-online.de');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.password', 'IMAP Passwort', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.port', 'IMAP Port (993 = SSL)', '993');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('imap.username', 'IMAP Benutzername / E-Mail-Adresse', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('mail.absender-name', 'Anzeigename des Absenders fuer Mails ueber das Standard-Postfach (leer = nur Adresse)', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('mail.dokumente.absender-name', 'Anzeigename des Absenders fuer Ausgangsgeschaeftsdokumente (leer = nur Adresse)', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('mail.dokumente.from-address', 'Sichtbare Absender-Adresse fuer Ausgangsgeschaeftsdokumente (leer = SMTP-Benutzer)', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('smtp.dokumente.aktiv', 'Eigenes Mail-Konto fuer Ausgangsgeschaeftsdokumente verwenden (false = Standard-Konto)', 'false');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('smtp.dokumente.host', 'SMTP Mail-Server fuer Ausgangsgeschaeftsdokumente', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('smtp.dokumente.password', 'SMTP Passwort fuer Ausgangsgeschaeftsdokumente', '');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('smtp.dokumente.port', 'SMTP Port fuer Ausgangsgeschaeftsdokumente (465 = SSL)', '465');
INSERT INTO public.system_setting (setting_key, beschreibung, setting_value) VALUES ('smtp.dokumente.username', 'SMTP Benutzername / E-Mail-Adresse fuer Ausgangsgeschaeftsdokumente', '');


--
-- Data for Name: telefon_anruf; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: textbaustein; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: textbaustein_dokumenttyp_enum; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: textbaustein_placeholder; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: urlaubsantrag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: verbrauchsgegenstand; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: verteilungsschluessel; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: verteilungsschluessel_eintrag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: website_analytics_snapshot; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: werkstoff; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.werkstoff (dichte, pulverbeschichtungsgeeignet, verzinkungsgeeignet, id, anzeigename, beschichtungshinweis, name, werkstattname, werkstoffnorm) VALUES (7.850, true, true, 1, 'Baustahl S355J2 (hochfest)', 'Feuerverzinken und Pulverbeschichten moeglich, auch kombiniert (Duplex).', 'S355J2', 'Baustahl', 'EN 10025-2');
INSERT INTO public.werkstoff (dichte, pulverbeschichtungsgeeignet, verzinkungsgeeignet, id, anzeigename, beschichtungshinweis, name, werkstattname, werkstoffnorm) VALUES (8.000, true, false, 2, 'Edelstahl 1.4571 (seewasserfest)', 'Nicht feuerverzinken. Hoehere Bestaendigkeit als 1.4301, fuer Kuesten- und Poolbereich.', '1.4571', 'V4A', 'EN 10088-3');
INSERT INTO public.werkstoff (dichte, pulverbeschichtungsgeeignet, verzinkungsgeeignet, id, anzeigename, beschichtungshinweis, name, werkstattname, werkstoffnorm) VALUES (2.660, true, false, 3, 'Aluminium EN AW-5754 (Blech)', 'Nicht feuerverzinken. Gut umformbar und schweissbar - die uebliche Blechlegierung.', 'EN AW-5754', 'Alu', 'EN 573-3');
INSERT INTO public.werkstoff (dichte, pulverbeschichtungsgeeignet, verzinkungsgeeignet, id, anzeigename, beschichtungshinweis, name, werkstattname, werkstoffnorm) VALUES (7.850, true, false, 4, 'Stahlblech verzinkt (Sendzimir)', 'Bereits bandverzinkt - kein zweites Verzinken noetig. Pulverbeschichten geht.', 'DX51D+Z', 'Verzinkt', 'EN 10346');
INSERT INTO public.werkstoff (dichte, pulverbeschichtungsgeeignet, verzinkungsgeeignet, id, anzeigename, beschichtungshinweis, name, werkstattname, werkstoffnorm) VALUES (8.000, true, false, 5, 'Edelstahl 1.4404', 'Nicht feuerverzinken. Molybdaenlegiert, bestaendiger als 1.4301 - fuer Aussen- und Poolbereich.', '1.4404', 'V4A', 'EN 10088-2');


--
-- Data for Name: zaehlerstand; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zahlungsart; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 10, 1, 'Bar');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 20, 2, 'EC-Karte');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 30, 3, 'Überweisung');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 40, 4, 'Lastschrift');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 50, 5, 'Kreditkarte');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 60, 6, 'PayPal');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 70, 7, 'Scheck');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 80, 8, 'Rechnung');
INSERT INTO public.zahlungsart (aktiv, sortierung, id, bezeichnung) VALUES (true, 65, 9, 'Online-Zahlung');


--
-- Data for Name: zeitbuchung; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitbuchung_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitkontenmodell; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitkonto_korrektur; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitkonto_korrektur_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitkonto_pause; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: zeitkonto_version; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: abteilung_dokument_berechtigung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.abteilung_dokument_berechtigung_id_seq', 1, false);


--
-- Name: abteilung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.abteilung_id_seq', 1, false);


--
-- Name: abwesenheit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.abwesenheit_id_seq', 1, false);


--
-- Name: aenderungsgrund_katalog_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.aenderungsgrund_katalog_id_seq', 1, false);


--
-- Name: anfrage_dokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.anfrage_dokument_id_seq', 1, false);


--
-- Name: anfrage_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.anfrage_id_seq', 1, false);


--
-- Name: anfrage_notiz_bild_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.anfrage_notiz_bild_id_seq', 1, false);


--
-- Name: anfrage_notiz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.anfrage_notiz_id_seq', 1, false);


--
-- Name: arbeitsgang_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.arbeitsgang_id_seq', 1, false);


--
-- Name: arbeitsgang_stundensatz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.arbeitsgang_stundensatz_id_seq', 1, false);


--
-- Name: arbeitszeitart_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.arbeitszeitart_id_seq', 1, false);


--
-- Name: artikel_dokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.artikel_dokument_id_seq', 1, false);


--
-- Name: artikel_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.artikel_id_seq', 27, true);


--
-- Name: artikel_in_projekt_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.artikel_in_projekt_id_seq', 1, false);


--
-- Name: ausgangs_geschaeftsdokument_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ausgangs_geschaeftsdokument_audit_id_seq', 1, false);


--
-- Name: ausgangs_geschaeftsdokument_counter_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ausgangs_geschaeftsdokument_counter_id_seq', 1, false);


--
-- Name: ausgangs_geschaeftsdokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ausgangs_geschaeftsdokument_id_seq', 1, false);


--
-- Name: beleg_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.beleg_audit_id_seq', 1, false);


--
-- Name: beleg_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.beleg_id_seq', 1, false);


--
-- Name: beleg_kostenstellen_anteil_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.beleg_kostenstellen_anteil_id_seq', 1, false);


--
-- Name: beleg_position_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.beleg_position_id_seq', 1, false);


--
-- Name: bwa_position_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.bwa_position_id_seq', 1, false);


--
-- Name: bwa_upload_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.bwa_upload_id_seq', 1, false);


--
-- Name: datensatz_lock_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.datensatz_lock_id_seq', 1, false);


--
-- Name: dokument_freigabe_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dokument_freigabe_id_seq', 1, false);


--
-- Name: dokumentnummer_counter_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dokumentnummer_counter_id_seq', 1, false);


--
-- Name: email_absender_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_absender_id_seq', 1, false);


--
-- Name: email_attachment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_attachment_id_seq', 1, false);


--
-- Name: email_blacklist_entry_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_blacklist_entry_id_seq', 1, false);


--
-- Name: email_draft_attachment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_draft_attachment_id_seq', 1, false);


--
-- Name: email_draft_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_draft_id_seq', 1, false);


--
-- Name: email_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_id_seq', 1, false);


--
-- Name: email_signature_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_signature_id_seq', 1, true);


--
-- Name: email_signature_image_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_signature_image_id_seq', 1, false);


--
-- Name: email_text_template_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.email_text_template_id_seq', 15, true);


--
-- Name: feiertag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.feiertag_id_seq', 1, false);


--
-- Name: firma_kostenstelle_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.firma_kostenstelle_id_seq', 1, false);


--
-- Name: formular_template_assignment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.formular_template_assignment_id_seq', 1, false);


--
-- Name: formular_template_textbaustein_default_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.formular_template_textbaustein_default_id_seq', 1, false);


--
-- Name: frontend_user_profile_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.frontend_user_profile_id_seq', 1, false);


--
-- Name: gewerk_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.gewerk_id_seq', 14, true);


--
-- Name: kalender_eintrag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kalender_eintrag_id_seq', 1, false);


--
-- Name: kasse_einstellung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kasse_einstellung_id_seq', 1, true);


--
-- Name: kassenbuch_monatsabschluss_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kassenbuch_monatsabschluss_id_seq', 1, false);


--
-- Name: kassenzaehlung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kassenzaehlung_id_seq', 1, false);


--
-- Name: kategorie_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kategorie_id_seq', 9, true);


--
-- Name: kontakt_rufnummer_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kontakt_rufnummer_id_seq', 1, false);


--
-- Name: kostenposition_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kostenposition_id_seq', 1, false);


--
-- Name: krankenkasse_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.krankenkasse_id_seq', 13, true);


--
-- Name: kunde_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kunde_id_seq', 1, false);


--
-- Name: kunde_notiz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.kunde_notiz_id_seq', 1, false);


--
-- Name: langzeitkrankmeldung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.langzeitkrankmeldung_id_seq', 1, false);


--
-- Name: langzeitkrankmeldung_phase_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.langzeitkrankmeldung_phase_id_seq', 1, false);


--
-- Name: leistung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leistung_id_seq', 1, false);


--
-- Name: lieferant_bild_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_bild_id_seq', 1, false);


--
-- Name: lieferant_dokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_dokument_id_seq', 1, false);


--
-- Name: lieferant_dokument_position_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_dokument_position_id_seq', 1, false);


--
-- Name: lieferant_dokument_projekt_anteil_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_dokument_projekt_anteil_id_seq', 1, false);


--
-- Name: lieferant_notiz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_notiz_id_seq', 1, false);


--
-- Name: lieferant_reklamation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferant_reklamation_id_seq', 1, false);


--
-- Name: lieferanten_artikel_preise_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferanten_artikel_preise_id_seq', 1, false);


--
-- Name: lieferanten_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lieferanten_id_seq', 1, false);


--
-- Name: lohnabrechnung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lohnabrechnung_id_seq', 1, false);


--
-- Name: materialkosten_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.materialkosten_id_seq', 1, false);


--
-- Name: miete_kostenstelle_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.miete_kostenstelle_id_seq', 1, false);


--
-- Name: mietobjekt_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mietobjekt_id_seq', 1, false);


--
-- Name: mietpartei_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mietpartei_id_seq', 1, false);


--
-- Name: mitarbeiter_dokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mitarbeiter_dokument_id_seq', 1, false);


--
-- Name: mitarbeiter_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mitarbeiter_id_seq', 1, true);


--
-- Name: mitarbeiter_notiz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mitarbeiter_notiz_id_seq', 1, false);


--
-- Name: mitarbeiter_stundenlohn_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.mitarbeiter_stundenlohn_id_seq', 1, false);


--
-- Name: monats_saldo_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.monats_saldo_id_seq', 1, false);


--
-- Name: monatsabschluss_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.monatsabschluss_audit_id_seq', 1, false);


--
-- Name: ooo_reply_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ooo_reply_log_id_seq', 1, false);


--
-- Name: out_of_office_schedule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.out_of_office_schedule_id_seq', 1, false);


--
-- Name: produktkategorie_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.produktkategorie_id_seq', 1, false);


--
-- Name: projekt_dokument_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.projekt_dokument_id_seq', 1, false);


--
-- Name: projekt_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.projekt_id_seq', 1, false);


--
-- Name: projekt_notiz_bild_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.projekt_notiz_bild_id_seq', 1, false);


--
-- Name: projekt_notiz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.projekt_notiz_id_seq', 1, false);


--
-- Name: projekt_produktkategorie_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.projekt_produktkategorie_id_seq', 1, false);


--
-- Name: push_subscription_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.push_subscription_id_seq', 1, false);


--
-- Name: raum_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.raum_id_seq', 1, false);


--
-- Name: sachkonto_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sachkonto_id_seq', 49, true);


--
-- Name: schnittbilder_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.schnittbilder_id_seq', 1, false);


--
-- Name: spam_model_stats_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.spam_model_stats_id_seq', 2, true);


--
-- Name: spam_token_count_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.spam_token_count_id_seq', 1, false);


--
-- Name: sprachnachricht_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sprachnachricht_id_seq', 1, false);


--
-- Name: steuerberater_ansprechpartner_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.steuerberater_ansprechpartner_id_seq', 1, false);


--
-- Name: steuerberater_kontakt_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.steuerberater_kontakt_id_seq', 1, false);


--
-- Name: sv_satz_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.sv_satz_id_seq', 11, true);


--
-- Name: telefon_anruf_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.telefon_anruf_id_seq', 1, false);


--
-- Name: textbaustein_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.textbaustein_id_seq', 1, false);


--
-- Name: urlaubsantrag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.urlaubsantrag_id_seq', 1, false);


--
-- Name: verbrauchsgegenstand_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.verbrauchsgegenstand_id_seq', 1, false);


--
-- Name: verteilungsschluessel_eintrag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.verteilungsschluessel_eintrag_id_seq', 1, false);


--
-- Name: verteilungsschluessel_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.verteilungsschluessel_id_seq', 1, false);


--
-- Name: website_analytics_snapshot_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.website_analytics_snapshot_id_seq', 1, false);


--
-- Name: werkstoff_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.werkstoff_id_seq', 5, true);


--
-- Name: zaehlerstand_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zaehlerstand_id_seq', 1, false);


--
-- Name: zahlungsart_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zahlungsart_id_seq', 9, true);


--
-- Name: zeitbuchung_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitbuchung_audit_id_seq', 1, false);


--
-- Name: zeitbuchung_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitbuchung_id_seq', 1, false);


--
-- Name: zeitkontenmodell_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitkontenmodell_id_seq', 1, false);


--
-- Name: zeitkonto_korrektur_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitkonto_korrektur_audit_id_seq', 1, false);


--
-- Name: zeitkonto_korrektur_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitkonto_korrektur_id_seq', 1, false);


--
-- Name: zeitkonto_pause_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitkonto_pause_id_seq', 1, false);


--
-- Name: zeitkonto_version_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.zeitkonto_version_id_seq', 1, false);


--
-- Name: abteilung_dokument_berechtigung abteilung_dokument_berechtigung_abteilung_id_dokument_typ_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung_dokument_berechtigung
    ADD CONSTRAINT abteilung_dokument_berechtigung_abteilung_id_dokument_typ_key UNIQUE (abteilung_id, dokument_typ);


--
-- Name: abteilung_dokument_berechtigung abteilung_dokument_berechtigung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung_dokument_berechtigung
    ADD CONSTRAINT abteilung_dokument_berechtigung_pkey PRIMARY KEY (id);


--
-- Name: abteilung abteilung_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung
    ADD CONSTRAINT abteilung_name_key UNIQUE (name);


--
-- Name: abteilung abteilung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung
    ADD CONSTRAINT abteilung_pkey PRIMARY KEY (id);


--
-- Name: abwesenheit abwesenheit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT abwesenheit_pkey PRIMARY KEY (id);


--
-- Name: aenderungsgrund_katalog aenderungsgrund_katalog_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aenderungsgrund_katalog
    ADD CONSTRAINT aenderungsgrund_katalog_code_key UNIQUE (code);


--
-- Name: aenderungsgrund_katalog aenderungsgrund_katalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aenderungsgrund_katalog
    ADD CONSTRAINT aenderungsgrund_katalog_pkey PRIMARY KEY (id);


--
-- Name: anfrage_dokument anfrage_dokument_gespeicherter_dateiname_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_dokument
    ADD CONSTRAINT anfrage_dokument_gespeicherter_dateiname_key UNIQUE (gespeicherter_dateiname);


--
-- Name: anfrage_dokument anfrage_dokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_dokument
    ADD CONSTRAINT anfrage_dokument_pkey PRIMARY KEY (id);


--
-- Name: anfrage_geschaeftsdokument anfrage_geschaeftsdokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_geschaeftsdokument
    ADD CONSTRAINT anfrage_geschaeftsdokument_pkey PRIMARY KEY (id);


--
-- Name: anfrage_notiz_bild anfrage_notiz_bild_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz_bild
    ADD CONSTRAINT anfrage_notiz_bild_pkey PRIMARY KEY (id);


--
-- Name: anfrage_notiz anfrage_notiz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz
    ADD CONSTRAINT anfrage_notiz_pkey PRIMARY KEY (id);


--
-- Name: anfrage anfrage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage
    ADD CONSTRAINT anfrage_pkey PRIMARY KEY (id);


--
-- Name: arbeitsgang arbeitsgang_beschreibung_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang
    ADD CONSTRAINT arbeitsgang_beschreibung_key UNIQUE (beschreibung);


--
-- Name: arbeitsgang arbeitsgang_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang
    ADD CONSTRAINT arbeitsgang_pkey PRIMARY KEY (id);


--
-- Name: arbeitsgang_stundensatz arbeitsgang_stundensatz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang_stundensatz
    ADD CONSTRAINT arbeitsgang_stundensatz_pkey PRIMARY KEY (id);


--
-- Name: arbeitszeitart arbeitszeitart_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitszeitart
    ADD CONSTRAINT arbeitszeitart_pkey PRIMARY KEY (id);


--
-- Name: artikel artikel_artikelnummer_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel
    ADD CONSTRAINT artikel_artikelnummer_key UNIQUE (artikelnummer);


--
-- Name: artikel_dokument artikel_dokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_dokument
    ADD CONSTRAINT artikel_dokument_pkey PRIMARY KEY (id);


--
-- Name: artikel_hilfsstoffe artikel_hilfsstoffe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_hilfsstoffe
    ADD CONSTRAINT artikel_hilfsstoffe_pkey PRIMARY KEY (id);


--
-- Name: artikel_in_projekt artikel_in_projekt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt
    ADD CONSTRAINT artikel_in_projekt_pkey PRIMARY KEY (id);


--
-- Name: artikel artikel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel
    ADD CONSTRAINT artikel_pkey PRIMARY KEY (id);


--
-- Name: artikel_werkstoffe artikel_werkstoffe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_werkstoffe
    ADD CONSTRAINT artikel_werkstoffe_pkey PRIMARY KEY (id);


--
-- Name: audit_chain_state audit_chain_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_chain_state
    ADD CONSTRAINT audit_chain_state_pkey PRIMARY KEY (id);


--
-- Name: ausgangs_geschaeftsdokument_audit ausgangs_geschaeftsdokument_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument_audit
    ADD CONSTRAINT ausgangs_geschaeftsdokument_audit_pkey PRIMARY KEY (id);


--
-- Name: ausgangs_geschaeftsdokument_counter ausgangs_geschaeftsdokument_counter_monat_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument_counter
    ADD CONSTRAINT ausgangs_geschaeftsdokument_counter_monat_key_key UNIQUE (monat_key);


--
-- Name: ausgangs_geschaeftsdokument_counter ausgangs_geschaeftsdokument_counter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument_counter
    ADD CONSTRAINT ausgangs_geschaeftsdokument_counter_pkey PRIMARY KEY (id);


--
-- Name: ausgangs_geschaeftsdokument ausgangs_geschaeftsdokument_dokument_nummer_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT ausgangs_geschaeftsdokument_dokument_nummer_key UNIQUE (dokument_nummer);


--
-- Name: ausgangs_geschaeftsdokument ausgangs_geschaeftsdokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT ausgangs_geschaeftsdokument_pkey PRIMARY KEY (id);


--
-- Name: beleg_audit_chain_state beleg_audit_chain_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_audit_chain_state
    ADD CONSTRAINT beleg_audit_chain_state_pkey PRIMARY KEY (id);


--
-- Name: beleg_audit beleg_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_audit
    ADD CONSTRAINT beleg_audit_pkey PRIMARY KEY (id);


--
-- Name: beleg_kostenstellen_anteil beleg_kostenstellen_anteil_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_kostenstellen_anteil
    ADD CONSTRAINT beleg_kostenstellen_anteil_pkey PRIMARY KEY (id);


--
-- Name: beleg beleg_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT beleg_pkey PRIMARY KEY (id);


--
-- Name: beleg_position beleg_position_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_position
    ADD CONSTRAINT beleg_position_pkey PRIMARY KEY (id);


--
-- Name: bwa_position bwa_position_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_position
    ADD CONSTRAINT bwa_position_pkey PRIMARY KEY (id);


--
-- Name: bwa_upload bwa_upload_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_upload
    ADD CONSTRAINT bwa_upload_pkey PRIMARY KEY (id);


--
-- Name: datensatz_lock datensatz_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datensatz_lock
    ADD CONSTRAINT datensatz_lock_pkey PRIMARY KEY (id);


--
-- Name: datev_konfiguration datev_konfiguration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datev_konfiguration
    ADD CONSTRAINT datev_konfiguration_pkey PRIMARY KEY (id);


--
-- Name: datev_personalnummer datev_personalnummer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datev_personalnummer
    ADD CONSTRAINT datev_personalnummer_pkey PRIMARY KEY (mitarbeiter_id);


--
-- Name: dokument_freigabe dokument_freigabe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokument_freigabe
    ADD CONSTRAINT dokument_freigabe_pkey PRIMARY KEY (id);


--
-- Name: dokument_freigabe dokument_freigabe_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokument_freigabe
    ADD CONSTRAINT dokument_freigabe_uuid_key UNIQUE (uuid);


--
-- Name: dokumentnummer_counter dokumentnummer_counter_month_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokumentnummer_counter
    ADD CONSTRAINT dokumentnummer_counter_month_key_key UNIQUE (month_key);


--
-- Name: dokumentnummer_counter dokumentnummer_counter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dokumentnummer_counter
    ADD CONSTRAINT dokumentnummer_counter_pkey PRIMARY KEY (id);


--
-- Name: email_absender email_absender_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_absender
    ADD CONSTRAINT email_absender_pkey PRIMARY KEY (id);


--
-- Name: email_attachment email_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_attachment
    ADD CONSTRAINT email_attachment_pkey PRIMARY KEY (id);


--
-- Name: email_blacklist_entry email_blacklist_entry_email_address_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_blacklist_entry
    ADD CONSTRAINT email_blacklist_entry_email_address_key UNIQUE (email_address);


--
-- Name: email_blacklist_entry email_blacklist_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_blacklist_entry
    ADD CONSTRAINT email_blacklist_entry_pkey PRIMARY KEY (id);


--
-- Name: email_draft_attachment email_draft_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft_attachment
    ADD CONSTRAINT email_draft_attachment_pkey PRIMARY KEY (id);


--
-- Name: email_draft email_draft_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft
    ADD CONSTRAINT email_draft_pkey PRIMARY KEY (id);


--
-- Name: email email_message_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT email_message_id_key UNIQUE (message_id);


--
-- Name: email email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT email_pkey PRIMARY KEY (id);


--
-- Name: email_signature_image email_signature_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_signature_image
    ADD CONSTRAINT email_signature_image_pkey PRIMARY KEY (id);


--
-- Name: email_signature email_signature_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_signature
    ADD CONSTRAINT email_signature_pkey PRIMARY KEY (id);


--
-- Name: email_text_template email_text_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_text_template
    ADD CONSTRAINT email_text_template_pkey PRIMARY KEY (id);


--
-- Name: entity_last_accessed entity_last_accessed_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_last_accessed
    ADD CONSTRAINT entity_last_accessed_pkey PRIMARY KEY (entity_id, user_id, entity_type);


--
-- Name: feiertag feiertag_datum_bundesland_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feiertag
    ADD CONSTRAINT feiertag_datum_bundesland_key UNIQUE (datum, bundesland);


--
-- Name: feiertag feiertag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feiertag
    ADD CONSTRAINT feiertag_pkey PRIMARY KEY (id);


--
-- Name: firma_kostenstelle firma_kostenstelle_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_kostenstelle
    ADD CONSTRAINT firma_kostenstelle_name_key UNIQUE (name);


--
-- Name: firma_kostenstelle firma_kostenstelle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firma_kostenstelle
    ADD CONSTRAINT firma_kostenstelle_pkey PRIMARY KEY (id);


--
-- Name: firmeninformation firmeninformation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firmeninformation
    ADD CONSTRAINT firmeninformation_pkey PRIMARY KEY (id);


--
-- Name: formular_template_assignment formular_template_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_assignment
    ADD CONSTRAINT formular_template_assignment_pkey PRIMARY KEY (id);


--
-- Name: formular_template_assignment formular_template_assignment_template_name_dokumenttyp_enum_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_assignment
    ADD CONSTRAINT formular_template_assignment_template_name_dokumenttyp_enum_key UNIQUE (template_name, dokumenttyp_enum, user_id);


--
-- Name: formular_template_textbaustein_default formular_template_textbaustein_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_textbaustein_default
    ADD CONSTRAINT formular_template_textbaustein_default_pkey PRIMARY KEY (id);


--
-- Name: frontend_user_profile frontend_user_profile_mitarbeiter_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT frontend_user_profile_mitarbeiter_id_key UNIQUE (mitarbeiter_id);


--
-- Name: frontend_user_profile frontend_user_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT frontend_user_profile_pkey PRIMARY KEY (id);


--
-- Name: frontend_user_profile_role frontend_user_profile_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile_role
    ADD CONSTRAINT frontend_user_profile_role_pkey PRIMARY KEY (frontend_user_profile_id, role_name);


--
-- Name: gewerk gewerk_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gewerk
    ADD CONSTRAINT gewerk_name_key UNIQUE (name);


--
-- Name: gewerk gewerk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gewerk
    ADD CONSTRAINT gewerk_pkey PRIMARY KEY (id);


--
-- Name: kalender_eintrag kalender_eintrag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT kalender_eintrag_pkey PRIMARY KEY (id);


--
-- Name: kalender_eintrag_teilnehmer kalender_eintrag_teilnehmer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag_teilnehmer
    ADD CONSTRAINT kalender_eintrag_teilnehmer_pkey PRIMARY KEY (kalender_eintrag_id, mitarbeiter_id);


--
-- Name: kasse_einstellung kasse_einstellung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kasse_einstellung
    ADD CONSTRAINT kasse_einstellung_pkey PRIMARY KEY (id);


--
-- Name: kassenbuch_monatsabschluss kassenbuch_monatsabschluss_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kassenbuch_monatsabschluss
    ADD CONSTRAINT kassenbuch_monatsabschluss_pkey PRIMARY KEY (id);


--
-- Name: kassenzaehlung kassenzaehlung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kassenzaehlung
    ADD CONSTRAINT kassenzaehlung_pkey PRIMARY KEY (id);


--
-- Name: kategorie kategorie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategorie
    ADD CONSTRAINT kategorie_pkey PRIMARY KEY (id);


--
-- Name: kontakt_rufnummer kontakt_rufnummer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kontakt_rufnummer
    ADD CONSTRAINT kontakt_rufnummer_pkey PRIMARY KEY (id);


--
-- Name: kostenposition kostenposition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kostenposition
    ADD CONSTRAINT kostenposition_pkey PRIMARY KEY (id);


--
-- Name: krankenkasse krankenkasse_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.krankenkasse
    ADD CONSTRAINT krankenkasse_name_key UNIQUE (name);


--
-- Name: krankenkasse krankenkasse_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.krankenkasse
    ADD CONSTRAINT krankenkasse_pkey PRIMARY KEY (id);


--
-- Name: kunde kunde_kundennummer_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde
    ADD CONSTRAINT kunde_kundennummer_key UNIQUE (kundennummer);


--
-- Name: kunde_notiz kunde_notiz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde_notiz
    ADD CONSTRAINT kunde_notiz_pkey PRIMARY KEY (id);


--
-- Name: kunde kunde_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde
    ADD CONSTRAINT kunde_pkey PRIMARY KEY (id);


--
-- Name: kunden_emails kunden_emails_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunden_emails
    ADD CONSTRAINT kunden_emails_email_key UNIQUE (email);


--
-- Name: kunden_zaehler kunden_zaehler_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunden_zaehler
    ADD CONSTRAINT kunden_zaehler_pkey PRIMARY KEY (id);


--
-- Name: langzeitkrankmeldung_phase langzeitkrankmeldung_phase_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung_phase
    ADD CONSTRAINT langzeitkrankmeldung_phase_pkey PRIMARY KEY (id);


--
-- Name: langzeitkrankmeldung langzeitkrankmeldung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung
    ADD CONSTRAINT langzeitkrankmeldung_pkey PRIMARY KEY (id);


--
-- Name: leistung leistung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leistung
    ADD CONSTRAINT leistung_pkey PRIMARY KEY (id);


--
-- Name: lieferant_bild lieferant_bild_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_bild
    ADD CONSTRAINT lieferant_bild_pkey PRIMARY KEY (id);


--
-- Name: lieferant_dokument lieferant_dokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument
    ADD CONSTRAINT lieferant_dokument_pkey PRIMARY KEY (id);


--
-- Name: lieferant_dokument_position lieferant_dokument_position_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_position
    ADD CONSTRAINT lieferant_dokument_position_pkey PRIMARY KEY (id);


--
-- Name: lieferant_dokument_projekt_anteil lieferant_dokument_projekt_anteil_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil
    ADD CONSTRAINT lieferant_dokument_projekt_anteil_pkey PRIMARY KEY (id);


--
-- Name: lieferant_dokument_verknuepfung_gesperrt lieferant_dokument_verknuepfung_gesperrt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung_gesperrt
    ADD CONSTRAINT lieferant_dokument_verknuepfung_gesperrt_pkey PRIMARY KEY (dokument_id, verknuepft_id);


--
-- Name: lieferant_dokument_verknuepfung lieferant_dokument_verknuepfung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung
    ADD CONSTRAINT lieferant_dokument_verknuepfung_pkey PRIMARY KEY (dokument_id, verknuepft_id);


--
-- Name: lieferant_geschaeftsdokument lieferant_geschaeftsdokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_geschaeftsdokument
    ADD CONSTRAINT lieferant_geschaeftsdokument_pkey PRIMARY KEY (id);


--
-- Name: lieferant_notiz lieferant_notiz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_notiz
    ADD CONSTRAINT lieferant_notiz_pkey PRIMARY KEY (id);


--
-- Name: lieferant_reklamation lieferant_reklamation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_reklamation
    ADD CONSTRAINT lieferant_reklamation_pkey PRIMARY KEY (id);


--
-- Name: lieferanten_artikel_preise lieferanten_artikel_preise_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_artikel_preise
    ADD CONSTRAINT lieferanten_artikel_preise_pkey PRIMARY KEY (id);


--
-- Name: lieferanten lieferanten_lieferantenname_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten
    ADD CONSTRAINT lieferanten_lieferantenname_key UNIQUE (lieferantenname);


--
-- Name: lieferanten lieferanten_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten
    ADD CONSTRAINT lieferanten_pkey PRIMARY KEY (id);


--
-- Name: lohnabrechnung lohnabrechnung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lohnabrechnung
    ADD CONSTRAINT lohnabrechnung_pkey PRIMARY KEY (id);


--
-- Name: materialkosten materialkosten_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materialkosten
    ADD CONSTRAINT materialkosten_pkey PRIMARY KEY (id);


--
-- Name: miete_kostenstelle miete_kostenstelle_mietobjekt_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.miete_kostenstelle
    ADD CONSTRAINT miete_kostenstelle_mietobjekt_id_name_key UNIQUE (mietobjekt_id, name);


--
-- Name: miete_kostenstelle miete_kostenstelle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.miete_kostenstelle
    ADD CONSTRAINT miete_kostenstelle_pkey PRIMARY KEY (id);


--
-- Name: mietobjekt mietobjekt_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietobjekt
    ADD CONSTRAINT mietobjekt_name_key UNIQUE (name);


--
-- Name: mietobjekt mietobjekt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietobjekt
    ADD CONSTRAINT mietobjekt_pkey PRIMARY KEY (id);


--
-- Name: mietpartei mietpartei_mietobjekt_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietpartei
    ADD CONSTRAINT mietpartei_mietobjekt_id_name_key UNIQUE (mietobjekt_id, name);


--
-- Name: mietpartei mietpartei_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietpartei
    ADD CONSTRAINT mietpartei_pkey PRIMARY KEY (id);


--
-- Name: mitarbeiter_abteilung mitarbeiter_abteilung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_abteilung
    ADD CONSTRAINT mitarbeiter_abteilung_pkey PRIMARY KEY (abteilung_id, mitarbeiter_id);


--
-- Name: mitarbeiter_dokument mitarbeiter_dokument_gespeicherter_dateiname_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_dokument
    ADD CONSTRAINT mitarbeiter_dokument_gespeicherter_dateiname_key UNIQUE (gespeicherter_dateiname);


--
-- Name: mitarbeiter_dokument mitarbeiter_dokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_dokument
    ADD CONSTRAINT mitarbeiter_dokument_pkey PRIMARY KEY (id);


--
-- Name: mitarbeiter mitarbeiter_login_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter
    ADD CONSTRAINT mitarbeiter_login_token_key UNIQUE (login_token);


--
-- Name: mitarbeiter_notiz mitarbeiter_notiz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_notiz
    ADD CONSTRAINT mitarbeiter_notiz_pkey PRIMARY KEY (id);


--
-- Name: mitarbeiter mitarbeiter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter
    ADD CONSTRAINT mitarbeiter_pkey PRIMARY KEY (id);


--
-- Name: mitarbeiter_stundenlohn mitarbeiter_stundenlohn_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_stundenlohn
    ADD CONSTRAINT mitarbeiter_stundenlohn_pkey PRIMARY KEY (id);


--
-- Name: monats_saldo monats_saldo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monats_saldo
    ADD CONSTRAINT monats_saldo_pkey PRIMARY KEY (id);


--
-- Name: monatsabschluss_audit monatsabschluss_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monatsabschluss_audit
    ADD CONSTRAINT monatsabschluss_audit_pkey PRIMARY KEY (id);


--
-- Name: ooo_reply_log ooo_reply_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ooo_reply_log
    ADD CONSTRAINT ooo_reply_log_pkey PRIMARY KEY (id);


--
-- Name: out_of_office_schedule out_of_office_schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.out_of_office_schedule
    ADD CONSTRAINT out_of_office_schedule_pkey PRIMARY KEY (id);


--
-- Name: produktkategorie produktkategorie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produktkategorie
    ADD CONSTRAINT produktkategorie_pkey PRIMARY KEY (id);


--
-- Name: projekt projekt_auftragsnummer_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt
    ADD CONSTRAINT projekt_auftragsnummer_key UNIQUE (auftragsnummer);


--
-- Name: projekt_dokument projekt_dokument_gespeicherter_dateiname_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument
    ADD CONSTRAINT projekt_dokument_gespeicherter_dateiname_key UNIQUE (gespeicherter_dateiname);


--
-- Name: projekt_dokument projekt_dokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument
    ADD CONSTRAINT projekt_dokument_pkey PRIMARY KEY (id);


--
-- Name: projekt_geschaeftsdokument projekt_geschaeftsdokument_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_geschaeftsdokument
    ADD CONSTRAINT projekt_geschaeftsdokument_pkey PRIMARY KEY (id);


--
-- Name: projekt_notiz_bild projekt_notiz_bild_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz_bild
    ADD CONSTRAINT projekt_notiz_bild_pkey PRIMARY KEY (id);


--
-- Name: projekt_notiz projekt_notiz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz
    ADD CONSTRAINT projekt_notiz_pkey PRIMARY KEY (id);


--
-- Name: projekt projekt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt
    ADD CONSTRAINT projekt_pkey PRIMARY KEY (id);


--
-- Name: projekt_produktkategorie projekt_produktkategorie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_produktkategorie
    ADD CONSTRAINT projekt_produktkategorie_pkey PRIMARY KEY (id);


--
-- Name: push_subscription push_subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscription
    ADD CONSTRAINT push_subscription_pkey PRIMARY KEY (id);


--
-- Name: raum raum_mietobjekt_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raum
    ADD CONSTRAINT raum_mietobjekt_id_name_key UNIQUE (mietobjekt_id, name);


--
-- Name: raum raum_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raum
    ADD CONSTRAINT raum_pkey PRIMARY KEY (id);


--
-- Name: sachkonto sachkonto_bezeichnung_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sachkonto
    ADD CONSTRAINT sachkonto_bezeichnung_key UNIQUE (bezeichnung);


--
-- Name: sachkonto sachkonto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sachkonto
    ADD CONSTRAINT sachkonto_pkey PRIMARY KEY (id);


--
-- Name: schnittbilder schnittbilder_bild_url_schnittbild_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schnittbilder
    ADD CONSTRAINT schnittbilder_bild_url_schnittbild_key UNIQUE (bild_url_schnittbild);


--
-- Name: schnittbilder schnittbilder_form_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schnittbilder
    ADD CONSTRAINT schnittbilder_form_key UNIQUE (form);


--
-- Name: schnittbilder schnittbilder_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schnittbilder
    ADD CONSTRAINT schnittbilder_pkey PRIMARY KEY (id);


--
-- Name: seen_sender_domain seen_sender_domain_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seen_sender_domain
    ADD CONSTRAINT seen_sender_domain_pkey PRIMARY KEY (domain);


--
-- Name: spam_model_stats spam_model_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_model_stats
    ADD CONSTRAINT spam_model_stats_pkey PRIMARY KEY (id);


--
-- Name: spam_model_stats spam_model_stats_stat_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_model_stats
    ADD CONSTRAINT spam_model_stats_stat_key_key UNIQUE (stat_key);


--
-- Name: spam_token_count spam_token_count_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_token_count
    ADD CONSTRAINT spam_token_count_pkey PRIMARY KEY (id);


--
-- Name: spam_token_count spam_token_count_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spam_token_count
    ADD CONSTRAINT spam_token_count_token_key UNIQUE (token);


--
-- Name: sprachnachricht sprachnachricht_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT sprachnachricht_pkey PRIMARY KEY (id);


--
-- Name: steuerberater_ansprechpartner steuerberater_ansprechpartner_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_ansprechpartner
    ADD CONSTRAINT steuerberater_ansprechpartner_pkey PRIMARY KEY (id);


--
-- Name: steuerberater_kontakt steuerberater_kontakt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_kontakt
    ADD CONSTRAINT steuerberater_kontakt_pkey PRIMARY KEY (id);


--
-- Name: sv_satz sv_satz_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sv_satz
    ADD CONSTRAINT sv_satz_pkey PRIMARY KEY (id);


--
-- Name: system_setting system_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_setting
    ADD CONSTRAINT system_setting_pkey PRIMARY KEY (setting_key);


--
-- Name: telefon_anruf telefon_anruf_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telefon_anruf
    ADD CONSTRAINT telefon_anruf_pkey PRIMARY KEY (id);


--
-- Name: textbaustein textbaustein_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.textbaustein
    ADD CONSTRAINT textbaustein_pkey PRIMARY KEY (id);


--
-- Name: abwesenheit uk_abwesenheit_mitarbeiter_datum_typ; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT uk_abwesenheit_mitarbeiter_datum_typ UNIQUE (mitarbeiter_id, datum, typ);


--
-- Name: datensatz_lock uk_datensatz_lock_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datensatz_lock
    ADD CONSTRAINT uk_datensatz_lock_target UNIQUE (entitaet_typ, entitaet_id);


--
-- Name: datev_personalnummer uk_datev_personalnummer_normalisiert; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datev_personalnummer
    ADD CONSTRAINT uk_datev_personalnummer_normalisiert UNIQUE (normalisiert);


--
-- Name: email_absender uk_email_absender_adresse; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_absender
    ADD CONSTRAINT uk_email_absender_adresse UNIQUE (email_adresse);


--
-- Name: email_text_template uk_email_text_template_doktyp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_text_template
    ADD CONSTRAINT uk_email_text_template_doktyp UNIQUE (dokument_typ);


--
-- Name: frontend_user_profile uk_frontend_user_profile_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT uk_frontend_user_profile_username UNIQUE (username);


--
-- Name: monats_saldo uk_monats_saldo_mitarbeiter_jahr_monat; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monats_saldo
    ADD CONSTRAINT uk_monats_saldo_mitarbeiter_jahr_monat UNIQUE (mitarbeiter_id, jahr, monat);


--
-- Name: ooo_reply_log uk_ooo_reply_log_schedule_sender; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ooo_reply_log
    ADD CONSTRAINT uk_ooo_reply_log_schedule_sender UNIQUE (schedule_id, sender_address);


--
-- Name: website_analytics_snapshot uk_website_analytics_snapshot_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_analytics_snapshot
    ADD CONSTRAINT uk_website_analytics_snapshot_date UNIQUE (snapshot_date);


--
-- Name: zeitbuchung_audit uk_zeitbuchung_audit_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung_audit
    ADD CONSTRAINT uk_zeitbuchung_audit_version UNIQUE (zeitbuchung_id, version);


--
-- Name: zeitbuchung uk_zeitbuchung_mitarbeiter_start; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT uk_zeitbuchung_mitarbeiter_start UNIQUE (mitarbeiter_id, start_zeit);


--
-- Name: zeitkonto_korrektur_audit uk_zeitkonto_korrektur_audit_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur_audit
    ADD CONSTRAINT uk_zeitkonto_korrektur_audit_version UNIQUE (zeitkonto_korrektur_id, version);


--
-- Name: zeitkonto_pause uk_zeitkonto_pause_mitarbeiter_von; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_pause
    ADD CONSTRAINT uk_zeitkonto_pause_mitarbeiter_von UNIQUE (mitarbeiter_id, gueltig_von);


--
-- Name: zeitkonto_version uk_zeitkonto_version_mitarbeiter_von; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_version
    ADD CONSTRAINT uk_zeitkonto_version_mitarbeiter_von UNIQUE (mitarbeiter_id, gueltig_von);


--
-- Name: urlaubsantrag urlaubsantrag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.urlaubsantrag
    ADD CONSTRAINT urlaubsantrag_pkey PRIMARY KEY (id);


--
-- Name: verbrauchsgegenstand verbrauchsgegenstand_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verbrauchsgegenstand
    ADD CONSTRAINT verbrauchsgegenstand_pkey PRIMARY KEY (id);


--
-- Name: verbrauchsgegenstand verbrauchsgegenstand_raum_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verbrauchsgegenstand
    ADD CONSTRAINT verbrauchsgegenstand_raum_id_name_key UNIQUE (raum_id, name);


--
-- Name: verteilungsschluessel_eintrag verteilungsschluessel_eintrag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel_eintrag
    ADD CONSTRAINT verteilungsschluessel_eintrag_pkey PRIMARY KEY (id);


--
-- Name: verteilungsschluessel verteilungsschluessel_mietobjekt_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel
    ADD CONSTRAINT verteilungsschluessel_mietobjekt_id_name_key UNIQUE (mietobjekt_id, name);


--
-- Name: verteilungsschluessel verteilungsschluessel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel
    ADD CONSTRAINT verteilungsschluessel_pkey PRIMARY KEY (id);


--
-- Name: website_analytics_snapshot website_analytics_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_analytics_snapshot
    ADD CONSTRAINT website_analytics_snapshot_pkey PRIMARY KEY (id);


--
-- Name: werkstoff werkstoff_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.werkstoff
    ADD CONSTRAINT werkstoff_name_key UNIQUE (name);


--
-- Name: werkstoff werkstoff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.werkstoff
    ADD CONSTRAINT werkstoff_pkey PRIMARY KEY (id);


--
-- Name: zaehlerstand zaehlerstand_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zaehlerstand
    ADD CONSTRAINT zaehlerstand_pkey PRIMARY KEY (id);


--
-- Name: zaehlerstand zaehlerstand_verbrauchsgegenstand_id_abrechnungs_jahr_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zaehlerstand
    ADD CONSTRAINT zaehlerstand_verbrauchsgegenstand_id_abrechnungs_jahr_key UNIQUE (verbrauchsgegenstand_id, abrechnungs_jahr);


--
-- Name: zahlungsart zahlungsart_bezeichnung_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zahlungsart
    ADD CONSTRAINT zahlungsart_bezeichnung_key UNIQUE (bezeichnung);


--
-- Name: zahlungsart zahlungsart_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zahlungsart
    ADD CONSTRAINT zahlungsart_pkey PRIMARY KEY (id);


--
-- Name: zeitbuchung_audit zeitbuchung_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung_audit
    ADD CONSTRAINT zeitbuchung_audit_pkey PRIMARY KEY (id);


--
-- Name: zeitbuchung zeitbuchung_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT zeitbuchung_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: zeitbuchung zeitbuchung_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT zeitbuchung_pkey PRIMARY KEY (id);


--
-- Name: zeitbuchung zeitbuchung_stop_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT zeitbuchung_stop_idempotency_key_key UNIQUE (stop_idempotency_key);


--
-- Name: zeitkontenmodell zeitkontenmodell_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkontenmodell
    ADD CONSTRAINT zeitkontenmodell_pkey PRIMARY KEY (id);


--
-- Name: zeitkonto_korrektur_audit zeitkonto_korrektur_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur_audit
    ADD CONSTRAINT zeitkonto_korrektur_audit_pkey PRIMARY KEY (id);


--
-- Name: zeitkonto_korrektur zeitkonto_korrektur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur
    ADD CONSTRAINT zeitkonto_korrektur_pkey PRIMARY KEY (id);


--
-- Name: zeitkonto_pause zeitkonto_pause_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_pause
    ADD CONSTRAINT zeitkonto_pause_pkey PRIMARY KEY (id);


--
-- Name: zeitkonto_version zeitkonto_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_version
    ADD CONSTRAINT zeitkonto_version_pkey PRIMARY KEY (id);


--
-- Name: abwesenheit__FKp1m4m9m370l0ekoohchtq9p95; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "abwesenheit__FKp1m4m9m370l0ekoohchtq9p95" ON public.abwesenheit USING btree (langzeitkrankmeldung_id);


--
-- Name: abwesenheit__FKq2767pce39mwtcrdj3klfwx6d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "abwesenheit__FKq2767pce39mwtcrdj3klfwx6d" ON public.abwesenheit USING btree (urlaubsantrag_id);


--
-- Name: abwesenheit__FKt0ubrnf7d1gpsymykqurc95ih; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "abwesenheit__FKt0ubrnf7d1gpsymykqurc95ih" ON public.abwesenheit USING btree (langzeitkrankmeldung_phase_id);


--
-- Name: anfrage__FK74hhvjetu7xw9kbg0hnq4f94s; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage__FK74hhvjetu7xw9kbg0hnq4f94s" ON public.anfrage USING btree (projekt_id);


--
-- Name: anfrage__FK7tcxpwd7j1238baugoddm2c6c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage__FK7tcxpwd7j1238baugoddm2c6c" ON public.anfrage USING btree (kunde_id);


--
-- Name: anfrage_dokument__FKkakl5w3pite8krxx0qo896ov4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage_dokument__FKkakl5w3pite8krxx0qo896ov4" ON public.anfrage_dokument USING btree (anfrage_id);


--
-- Name: anfrage_kunden_emails__FKpinl3l31eh8d690d23q86g88s; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage_kunden_emails__FKpinl3l31eh8d690d23q86g88s" ON public.anfrage_kunden_emails USING btree (anfrage_id);


--
-- Name: anfrage_notiz__FK8b3ncalux4oq9ry9repssbnfs; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage_notiz__FK8b3ncalux4oq9ry9repssbnfs" ON public.anfrage_notiz USING btree (mitarbeiter_id);


--
-- Name: anfrage_notiz__FKdv65icmxtwcf1s2e5j63au1af; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage_notiz__FKdv65icmxtwcf1s2e5j63au1af" ON public.anfrage_notiz USING btree (anfrage_id);


--
-- Name: anfrage_notiz_bild__FK27l5iks86tywcvido9yxtv5e1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "anfrage_notiz_bild__FK27l5iks86tywcvido9yxtv5e1" ON public.anfrage_notiz_bild USING btree (notiz_id);


--
-- Name: arbeitsgang__FK6n88tt8b8w4r1opg8ernqera9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "arbeitsgang__FK6n88tt8b8w4r1opg8ernqera9" ON public.arbeitsgang USING btree (abteilung_id);


--
-- Name: arbeitsgang_stundensatz__FK9n1dvl3im1swo1dw4ctybcs41; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "arbeitsgang_stundensatz__FK9n1dvl3im1swo1dw4ctybcs41" ON public.arbeitsgang_stundensatz USING btree (arbeitsgang_id);


--
-- Name: artikel__FK3eve2o35lujpa0um9h5kiakh1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel__FK3eve2o35lujpa0um9h5kiakh1" ON public.artikel USING btree (werkstoff_id);


--
-- Name: artikel__FKab40tv5rnynmyk9fk0uu1o24r; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel__FKab40tv5rnynmyk9fk0uu1o24r" ON public.artikel USING btree (kategorie_id);


--
-- Name: artikel__ix_artikel_profilform; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX artikel__ix_artikel_profilform ON public.artikel USING btree (profilform);


--
-- Name: artikel_dokument__idx_artikel_dokument_artikel_typ; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX artikel_dokument__idx_artikel_dokument_artikel_typ ON public.artikel_dokument USING btree (artikel_id, typ);


--
-- Name: artikel_in_projekt__FK5dlbaf5e0coskoalc5drxmf9m; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel_in_projekt__FK5dlbaf5e0coskoalc5drxmf9m" ON public.artikel_in_projekt USING btree (lieferanten_artikel_preis_id);


--
-- Name: artikel_in_projekt__FK6tk7id8jx0b4jw05lqwr2k5es; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel_in_projekt__FK6tk7id8jx0b4jw05lqwr2k5es" ON public.artikel_in_projekt USING btree (projekt_id);


--
-- Name: artikel_in_projekt__FKbff4hugr1a1yb7wpntofif4lj; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel_in_projekt__FKbff4hugr1a1yb7wpntofif4lj" ON public.artikel_in_projekt USING btree (artikel_id);


--
-- Name: artikel_in_projekt__FKcntoko2hycpioejmu3pjx9wv9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "artikel_in_projekt__FKcntoko2hycpioejmu3pjx9wv9" ON public.artikel_in_projekt USING btree (lieferant_id);


--
-- Name: artikel_in_projekt__ix_aip_preisstand; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX artikel_in_projekt__ix_aip_preisstand ON public.artikel_in_projekt USING btree (lieferanten_artikel_preis_id);


--
-- Name: ausgangs_geschaeftsdokument__FK4tpqnu19710wax59ela3bkggm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ausgangs_geschaeftsdokument__FK4tpqnu19710wax59ela3bkggm" ON public.ausgangs_geschaeftsdokument USING btree (vorgaenger_id);


--
-- Name: ausgangs_geschaeftsdokument__FKlgtye9xh70mbopblv0t2p5u17; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ausgangs_geschaeftsdokument__FKlgtye9xh70mbopblv0t2p5u17" ON public.ausgangs_geschaeftsdokument USING btree (anfrage_id);


--
-- Name: ausgangs_geschaeftsdokument__FKlllmb6a21xp4buuykrebpws5i; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ausgangs_geschaeftsdokument__FKlllmb6a21xp4buuykrebpws5i" ON public.ausgangs_geschaeftsdokument USING btree (kunde_id);


--
-- Name: ausgangs_geschaeftsdokument__FKn2517m61de0rysg7uokfa8ac7; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ausgangs_geschaeftsdokument__FKn2517m61de0rysg7uokfa8ac7" ON public.ausgangs_geschaeftsdokument USING btree (projekt_id);


--
-- Name: ausgangs_geschaeftsdokument__FKqllc9cls1j4n5ccy55faye7l9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ausgangs_geschaeftsdokument__FKqllc9cls1j4n5ccy55faye7l9" ON public.ausgangs_geschaeftsdokument USING btree (erstellt_von_id);


--
-- Name: ausgangs_geschaeftsdokument_audit__idx_audit_aktion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ausgangs_geschaeftsdokument_audit__idx_audit_aktion ON public.ausgangs_geschaeftsdokument_audit USING btree (aktion);


--
-- Name: ausgangs_geschaeftsdokument_audit__idx_audit_dokument_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ausgangs_geschaeftsdokument_audit__idx_audit_dokument_id ON public.ausgangs_geschaeftsdokument_audit USING btree (dokument_id);


--
-- Name: ausgangs_geschaeftsdokument_audit__idx_audit_geaendert_am; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ausgangs_geschaeftsdokument_audit__idx_audit_geaendert_am ON public.ausgangs_geschaeftsdokument_audit USING btree (geaendert_am);


--
-- Name: ausgangs_geschaeftsdokument_audit__uq_audit_chain_index; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ausgangs_geschaeftsdokument_audit__uq_audit_chain_index ON public.ausgangs_geschaeftsdokument_audit USING btree (chain_index);


--
-- Name: beleg__fk_beleg_ausgangsrechnung; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__fk_beleg_ausgangsrechnung ON public.beleg USING btree (ausgangsrechnung_id);


--
-- Name: beleg__fk_beleg_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__fk_beleg_lieferant ON public.beleg USING btree (lieferant_id);


--
-- Name: beleg__fk_beleg_validiert_von; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__fk_beleg_validiert_von ON public.beleg USING btree (validiert_von_id);


--
-- Name: beleg__idx_beleg_datum; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_datum ON public.beleg USING btree (beleg_datum);


--
-- Name: beleg__idx_beleg_festgeschrieben; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_festgeschrieben ON public.beleg USING btree (festgeschrieben, beleg_datum);


--
-- Name: beleg__idx_beleg_kategorie; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_kategorie ON public.beleg USING btree (beleg_kategorie);


--
-- Name: beleg__idx_beleg_kostenstelle; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_kostenstelle ON public.beleg USING btree (kostenstelle_id, beleg_datum);


--
-- Name: beleg__idx_beleg_sachkonto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_sachkonto ON public.beleg USING btree (sachkonto_id);


--
-- Name: beleg__idx_beleg_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_status ON public.beleg USING btree (status);


--
-- Name: beleg__idx_beleg_uploaded_by_upload_datum; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg__idx_beleg_uploaded_by_upload_datum ON public.beleg USING btree (uploaded_by_id, upload_datum);


--
-- Name: beleg__uq_beleg_laufende_nummer; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX beleg__uq_beleg_laufende_nummer ON public.beleg USING btree (laufende_nummer);


--
-- Name: beleg_audit__idx_beleg_audit_beleg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_audit__idx_beleg_audit_beleg ON public.beleg_audit USING btree (beleg_id);


--
-- Name: beleg_audit__idx_beleg_audit_geaendert_am; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_audit__idx_beleg_audit_geaendert_am ON public.beleg_audit USING btree (geaendert_am);


--
-- Name: beleg_audit__uq_beleg_audit_chain_index; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX beleg_audit__uq_beleg_audit_chain_index ON public.beleg_audit USING btree (chain_index);


--
-- Name: beleg_kostenstellen_anteil__fk_bka_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_kostenstellen_anteil__fk_bka_user ON public.beleg_kostenstellen_anteil USING btree (zugeordnet_von_user_id);


--
-- Name: beleg_kostenstellen_anteil__idx_bka_beleg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_kostenstellen_anteil__idx_bka_beleg ON public.beleg_kostenstellen_anteil USING btree (beleg_id);


--
-- Name: beleg_kostenstellen_anteil__idx_bka_kostenstelle_jahr; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_kostenstellen_anteil__idx_bka_kostenstelle_jahr ON public.beleg_kostenstellen_anteil USING btree (kostenstelle_id, streckung_start_jahr);


--
-- Name: beleg_position__idx_beleg_position_beleg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX beleg_position__idx_beleg_position_beleg ON public.beleg_position USING btree (beleg_id, sortierung);


--
-- Name: bwa_position__FK5maeljbih33gg9i4sse6po1n5; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "bwa_position__FK5maeljbih33gg9i4sse6po1n5" ON public.bwa_position USING btree (bwa_upload_id);


--
-- Name: bwa_position__FKjkmp1yibx9emk2fh51t9stkvh; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "bwa_position__FKjkmp1yibx9emk2fh51t9stkvh" ON public.bwa_position USING btree (kostenstelle_id);


--
-- Name: bwa_upload__FK66q12gi4xqo63u2c8ea7hco5w; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "bwa_upload__FK66q12gi4xqo63u2c8ea7hco5w" ON public.bwa_upload USING btree (email_id);


--
-- Name: bwa_upload__FK9t7wn2ad5lclb957icluomdt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "bwa_upload__FK9t7wn2ad5lclb957icluomdt" ON public.bwa_upload USING btree (freigegeben_von_id);


--
-- Name: bwa_upload__FKmwf1exgkseq7yksx2j4xrr551; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "bwa_upload__FKmwf1exgkseq7yksx2j4xrr551" ON public.bwa_upload USING btree (steuerberater_id);


--
-- Name: datensatz_lock__idx_datensatz_lock_heartbeat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX datensatz_lock__idx_datensatz_lock_heartbeat ON public.datensatz_lock USING btree (last_heartbeat_at);


--
-- Name: dokument_freigabe__idx_dokument_freigabe_quelle; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dokument_freigabe__idx_dokument_freigabe_quelle ON public.dokument_freigabe USING btree (quell_typ, quell_dokument_id);


--
-- Name: dokument_freigabe__idx_dokument_freigabe_status_ablauf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dokument_freigabe__idx_dokument_freigabe_status_ablauf ON public.dokument_freigabe USING btree (status, ablauf_datum);


--
-- Name: email__FK8nttd3ghg0jdhfmcr9o1ow65s; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "email__FK8nttd3ghg0jdhfmcr9o1ow65s" ON public.email USING btree (steuerberater_id);


--
-- Name: email__FKdxywpbb3i2uhx6n5e4jpv2a9b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "email__FKdxywpbb3i2uhx6n5e4jpv2a9b" ON public.email USING btree (parent_email_id);


--
-- Name: email__idx_email_starred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email__idx_email_starred ON public.email USING btree (is_starred);


--
-- Name: email_attachment__FK361t957grpy6abpe8stl7rips; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "email_attachment__FK361t957grpy6abpe8stl7rips" ON public.email_attachment USING btree (lieferant_dokument_id);


--
-- Name: email_draft__fk_draft_anfrage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_draft__fk_draft_anfrage ON public.email_draft USING btree (anfrage_id);


--
-- Name: email_draft__fk_draft_projekt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_draft__fk_draft_projekt ON public.email_draft USING btree (projekt_id);


--
-- Name: email_draft__fk_draft_reply_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_draft__fk_draft_reply_email ON public.email_draft USING btree (reply_email_id);


--
-- Name: email_draft_attachment__fk_email_draft_attachment_draft; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_draft_attachment__fk_email_draft_attachment_draft ON public.email_draft_attachment USING btree (draft_id);


--
-- Name: email_signature_image__FKpfwt7v1tmth3t6xp79fip4du8; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "email_signature_image__FKpfwt7v1tmth3t6xp79fip4du8" ON public.email_signature_image USING btree (signature_id);


--
-- Name: entity_last_accessed__idx_entity_last_accessed_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX entity_last_accessed__idx_entity_last_accessed_lookup ON public.entity_last_accessed USING btree (user_id, entity_type, zugegriffen_am);


--
-- Name: firmeninformation__FK1cut60jy9u292yx9x339p43ay; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "firmeninformation__FK1cut60jy9u292yx9x339p43ay" ON public.firmeninformation USING btree (gewerk_id);


--
-- Name: formular_template_assignment__FKrqvgxham8n6b54ep6ybi1bx0a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "formular_template_assignment__FKrqvgxham8n6b54ep6ybi1bx0a" ON public.formular_template_assignment USING btree (user_id);


--
-- Name: formular_template_textbaustein_default__idx_fttd_textbaustein; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX formular_template_textbaustein_default__idx_fttd_textbaustein ON public.formular_template_textbaustein_default USING btree (textbaustein_id);


--
-- Name: frontend_user_profile__FKgy9nu6luuoih1woj77ivgqyoi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "frontend_user_profile__FKgy9nu6luuoih1woj77ivgqyoi" ON public.frontend_user_profile USING btree (email_absender_id);


--
-- Name: frontend_user_profile__FKr832ttbmukk4sdbp46wmdmpvv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "frontend_user_profile__FKr832ttbmukk4sdbp46wmdmpvv" ON public.frontend_user_profile USING btree (default_signature_id);


--
-- Name: idx_attachment_ai; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_ai ON public.email_attachment USING btree (ai_processed);


--
-- Name: idx_attachment_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_email ON public.email_attachment USING btree (email_id);


--
-- Name: idx_email_anfrage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_anfrage ON public.email USING btree (anfrage_id);


--
-- Name: idx_email_direction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_direction ON public.email USING btree (direction);


--
-- Name: idx_email_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_lieferant ON public.email USING btree (lieferant_id);


--
-- Name: idx_email_processing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_processing ON public.email USING btree (processing_status);


--
-- Name: idx_email_projekt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_projekt ON public.email USING btree (projekt_id);


--
-- Name: idx_email_sender_domain; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_sender_domain ON public.email USING btree (sender_domain);


--
-- Name: idx_email_sent_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_sent_at ON public.email USING btree (sent_at);


--
-- Name: idx_email_zuordnung; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_zuordnung ON public.email USING btree (zuordnung_typ);


--
-- Name: idx_fttd_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fttd_lookup ON public.formular_template_textbaustein_default USING btree (template_name, dokumenttyp, "position", sort_order);


--
-- Name: idx_lohnabrechnung_mitarbeiter; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lohnabrechnung_mitarbeiter ON public.lohnabrechnung USING btree (mitarbeiter_id);


--
-- Name: idx_lohnabrechnung_periode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lohnabrechnung_periode ON public.lohnabrechnung USING btree (jahr, monat);


--
-- Name: idx_lohnabrechnung_steuerberater; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lohnabrechnung_steuerberater ON public.lohnabrechnung USING btree (steuerberater_id);


--
-- Name: idx_monatsabschluss_audit_monat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monatsabschluss_audit_monat ON public.monatsabschluss_audit USING btree (mitarbeiter_id, jahr, monat, zeitpunkt, id);


--
-- Name: ix_lap_artikel_aktuell; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_lap_artikel_aktuell ON public.lieferanten_artikel_preise USING btree (artikel_id, aktuell, preis);


--
-- Name: ix_lap_lieferant_aen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_lap_lieferant_aen ON public.lieferanten_artikel_preise USING btree (lieferant_id, externe_artikelnummer);


--
-- Name: ix_lap_verlauf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_lap_verlauf ON public.lieferanten_artikel_preise USING btree (artikel_id, lieferant_id, preis_aenderungsdatum);


--
-- Name: kalender_eintrag__FK23nredk9cp87futr9wbd2w79u; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag__FK23nredk9cp87futr9wbd2w79u" ON public.kalender_eintrag USING btree (projekt_id);


--
-- Name: kalender_eintrag__FK825cvevrtryta7qgjmr138jt0; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag__FK825cvevrtryta7qgjmr138jt0" ON public.kalender_eintrag USING btree (anfrage_id);


--
-- Name: kalender_eintrag__FKfcwj8kcgklypce1r1miuo2g0m; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag__FKfcwj8kcgklypce1r1miuo2g0m" ON public.kalender_eintrag USING btree (ersteller_id);


--
-- Name: kalender_eintrag__FKfqbhhs48w4fnvr9e4fir9aoat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag__FKfqbhhs48w4fnvr9e4fir9aoat" ON public.kalender_eintrag USING btree (lieferant_id);


--
-- Name: kalender_eintrag__FKiulrt6wgh5ykpy8ejpl2j80ev; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag__FKiulrt6wgh5ykpy8ejpl2j80ev" ON public.kalender_eintrag USING btree (kunde_id);


--
-- Name: kalender_eintrag_teilnehmer__FKm0jouf8mkfhwu9bttn3rjji1m; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kalender_eintrag_teilnehmer__FKm0jouf8mkfhwu9bttn3rjji1m" ON public.kalender_eintrag_teilnehmer USING btree (mitarbeiter_id);


--
-- Name: kasse_einstellung__fk_kasse_privateinlage_konto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kasse_einstellung__fk_kasse_privateinlage_konto ON public.kasse_einstellung USING btree (privateinlage_sachkonto_id);


--
-- Name: kassenbuch_monatsabschluss__uq_monatsabschluss_jahr_monat; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX kassenbuch_monatsabschluss__uq_monatsabschluss_jahr_monat ON public.kassenbuch_monatsabschluss USING btree (jahr, monat);


--
-- Name: kassenzaehlung__idx_kassenzaehlung_stichtag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kassenzaehlung__idx_kassenzaehlung_stichtag ON public.kassenzaehlung USING btree (stichtag);


--
-- Name: kategorie__FK7rsp0w04q1kipndasbaxkhds9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kategorie__FK7rsp0w04q1kipndasbaxkhds9" ON public.kategorie USING btree (parent_kategorie_id);


--
-- Name: kontakt_rufnummer__fk_kontakt_rufnummer_kunde; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kontakt_rufnummer__fk_kontakt_rufnummer_kunde ON public.kontakt_rufnummer USING btree (kunde_id);


--
-- Name: kontakt_rufnummer__fk_kontakt_rufnummer_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kontakt_rufnummer__fk_kontakt_rufnummer_lieferant ON public.kontakt_rufnummer USING btree (lieferant_id);


--
-- Name: kontakt_rufnummer__fk_kontakt_rufnummer_steuerberater; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kontakt_rufnummer__fk_kontakt_rufnummer_steuerberater ON public.kontakt_rufnummer USING btree (steuerberater_id);


--
-- Name: kontakt_rufnummer__idx_kontakt_rufnummer_nummer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kontakt_rufnummer__idx_kontakt_rufnummer_nummer ON public.kontakt_rufnummer USING btree (nummer_normalisiert);


--
-- Name: kostenposition__FK5vo941bwtiwsi4sjr3eva5kwd; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kostenposition__FK5vo941bwtiwsi4sjr3eva5kwd" ON public.kostenposition USING btree (kostenstelle_id);


--
-- Name: kostenposition__FKaaog4r7chwdxf5igxppd7owwj; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kostenposition__FKaaog4r7chwdxf5igxppd7owwj" ON public.kostenposition USING btree (verteilungsschluessel_id);


--
-- Name: krankenkasse__idx_krankenkasse_aktiv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX krankenkasse__idx_krankenkasse_aktiv ON public.krankenkasse USING btree (aktiv);


--
-- Name: kunde_notiz__idx_kunde_notiz_kunde; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX kunde_notiz__idx_kunde_notiz_kunde ON public.kunde_notiz USING btree (kunde_id, erstellt_am);


--
-- Name: kunden_emails__FKocfqfi03pyffpsruvkng044x9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "kunden_emails__FKocfqfi03pyffpsruvkng044x9" ON public.kunden_emails USING btree (kunden_id);


--
-- Name: langzeitkrankmeldung__idx_langzeitkrankmeldung_mitarbeiter_begi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX langzeitkrankmeldung__idx_langzeitkrankmeldung_mitarbeiter_begi ON public.langzeitkrankmeldung USING btree (mitarbeiter_id, beginn);


--
-- Name: langzeitkrankmeldung_phase__idx_langzeitkrankmeldung_phase_meld; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX langzeitkrankmeldung_phase__idx_langzeitkrankmeldung_phase_meld ON public.langzeitkrankmeldung_phase USING btree (langzeitkrankmeldung_id, von_datum);


--
-- Name: leistung__FK7hofs5fopj2sk601jk5m69nse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "leistung__FK7hofs5fopj2sk601jk5m69nse" ON public.leistung USING btree (kategorie_id);


--
-- Name: lieferant_bild__FK3jsl58pjxxlgi2g1awao4wx5p; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_bild__FK3jsl58pjxxlgi2g1awao4wx5p" ON public.lieferant_bild USING btree (reklamation_id);


--
-- Name: lieferant_bild__FKdmj0fo47ntl2ckj3m9k5j6vgw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_bild__FKdmj0fo47ntl2ckj3m9k5j6vgw" ON public.lieferant_bild USING btree (lieferant_id);


--
-- Name: lieferant_bild__FKnbar2m456dfal5qvyr7i3lu1q; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_bild__FKnbar2m456dfal5qvyr7i3lu1q" ON public.lieferant_bild USING btree (mitarbeiter_id);


--
-- Name: lieferant_dokument__FK5fnimy797c9vpvc3si7h2ltsc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument__FK5fnimy797c9vpvc3si7h2ltsc" ON public.lieferant_dokument USING btree (lieferant_id);


--
-- Name: lieferant_dokument__FKg21j111exuttor4np4xu4ot24; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument__FKg21j111exuttor4np4xu4ot24" ON public.lieferant_dokument USING btree (uploaded_by_id);


--
-- Name: lieferant_dokument__FKn3js7qygrabjp3p8v9dvnrmoa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument__FKn3js7qygrabjp3p8v9dvnrmoa" ON public.lieferant_dokument USING btree (beleg_id);


--
-- Name: lieferant_dokument__FKo26ih6io5iveeuwsq7vykh08o; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument__FKo26ih6io5iveeuwsq7vykh08o" ON public.lieferant_dokument USING btree (attachment_id);


--
-- Name: lieferant_dokument__idx_lieferant_dokument_ausgeblendet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument__idx_lieferant_dokument_ausgeblendet ON public.lieferant_dokument USING btree (ausgeblendet);


--
-- Name: lieferant_dokument__idx_lieferant_dokument_beleg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument__idx_lieferant_dokument_beleg ON public.lieferant_dokument USING btree (beleg_id);


--
-- Name: lieferant_dokument_position__fk_ld_position_kostenstelle; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument_position__fk_ld_position_kostenstelle ON public.lieferant_dokument_position USING btree (kostenstelle_id);


--
-- Name: lieferant_dokument_position__fk_ld_position_projekt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument_position__fk_ld_position_projekt ON public.lieferant_dokument_position USING btree (projekt_id);


--
-- Name: lieferant_dokument_position__idx_ld_position_geschaeftsdokument; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument_position__idx_ld_position_geschaeftsdokument ON public.lieferant_dokument_position USING btree (geschaeftsdokument_id, position_nr);


--
-- Name: lieferant_dokument_projekt_anteil__FK8e8msmb5hg51mtnyy9wjdgyve; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument_projekt_anteil__FK8e8msmb5hg51mtnyy9wjdgyve" ON public.lieferant_dokument_projekt_anteil USING btree (zugeordnet_von_user_id);


--
-- Name: lieferant_dokument_projekt_anteil__FK9s9oi3un5efau5swc1kcvq708; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument_projekt_anteil__FK9s9oi3un5efau5swc1kcvq708" ON public.lieferant_dokument_projekt_anteil USING btree (projekt_id);


--
-- Name: lieferant_dokument_projekt_anteil__FKkxmiv0ydwaea4rsafh2sqgy7m; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument_projekt_anteil__FKkxmiv0ydwaea4rsafh2sqgy7m" ON public.lieferant_dokument_projekt_anteil USING btree (dokument_id);


--
-- Name: lieferant_dokument_projekt_anteil__FKo7j3pr0m7md9gh6cmi3iaxmnf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument_projekt_anteil__FKo7j3pr0m7md9gh6cmi3iaxmnf" ON public.lieferant_dokument_projekt_anteil USING btree (kostenstelle_id);


--
-- Name: lieferant_dokument_verknuepfung__FK557cmofj4yh8o7v3iprjkrolx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_dokument_verknuepfung__FK557cmofj4yh8o7v3iprjkrolx" ON public.lieferant_dokument_verknuepfung USING btree (verknuepft_id);


--
-- Name: lieferant_dokument_verknuepfung_gesperrt__idx_ld_verkn_gesperrt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferant_dokument_verknuepfung_gesperrt__idx_ld_verkn_gesperrt ON public.lieferant_dokument_verknuepfung_gesperrt USING btree (verknuepft_id);


--
-- Name: lieferant_notiz__FKny9ewg3u4n84j2n9452ojfn3c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_notiz__FKny9ewg3u4n84j2n9452ojfn3c" ON public.lieferant_notiz USING btree (lieferant_id);


--
-- Name: lieferant_reklamation__FKf4w70mefnf5c696mwhr3o07wy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_reklamation__FKf4w70mefnf5c696mwhr3o07wy" ON public.lieferant_reklamation USING btree (lieferant_id);


--
-- Name: lieferant_reklamation__FKi0vq2dxmy7twmr91rl4m9eq9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_reklamation__FKi0vq2dxmy7twmr91rl4m9eq9" ON public.lieferant_reklamation USING btree (lieferschein_id);


--
-- Name: lieferant_reklamation__FKs18k0x598vcgjadie04an4ykh; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferant_reklamation__FKs18k0x598vcgjadie04an4ykh" ON public.lieferant_reklamation USING btree (erstellt_von_id);


--
-- Name: lieferanten__FKgopne9hwl7n945f7ghdqc21mw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferanten__FKgopne9hwl7n945f7ghdqc21mw" ON public.lieferanten USING btree (standard_kostenstelle_id);


--
-- Name: lieferanten_artikel_preise__ix_lap_artikel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferanten_artikel_preise__ix_lap_artikel ON public.lieferanten_artikel_preise USING btree (artikel_id);


--
-- Name: lieferanten_artikel_preise__ix_lap_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lieferanten_artikel_preise__ix_lap_lieferant ON public.lieferanten_artikel_preise USING btree (lieferant_id);


--
-- Name: lieferanten_emails__FK7n9pw9uq6twnqk2gvohrasjqp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lieferanten_emails__FK7n9pw9uq6twnqk2gvohrasjqp" ON public.lieferanten_emails USING btree (lieferanten_id);


--
-- Name: lohnabrechnung__FKrvbnxfacldipkbyrkt9w8ylex; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "lohnabrechnung__FKrvbnxfacldipkbyrkt9w8ylex" ON public.lohnabrechnung USING btree (email_id);


--
-- Name: materialkosten__FK334hjof01f74lut1bq6n8t5lo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "materialkosten__FK334hjof01f74lut1bq6n8t5lo" ON public.materialkosten USING btree (projekt_id);


--
-- Name: materialkosten__FKgbrmg6ai07b0n46ff48knxx7c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "materialkosten__FKgbrmg6ai07b0n46ff48knxx7c" ON public.materialkosten USING btree (lieferant_id);


--
-- Name: miete_kostenstelle__FKds25jf6f8jo0wa3nmptgcnuv0; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "miete_kostenstelle__FKds25jf6f8jo0wa3nmptgcnuv0" ON public.miete_kostenstelle USING btree (standard_schluessel_id);


--
-- Name: mitarbeiter__FK28s44gxbnll5cni8jjse68nq1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "mitarbeiter__FK28s44gxbnll5cni8jjse68nq1" ON public.mitarbeiter USING btree (krankenkasse_id);


--
-- Name: mitarbeiter_abteilung__FK4mo0bw7rs0hulyfhqvfwledlv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "mitarbeiter_abteilung__FK4mo0bw7rs0hulyfhqvfwledlv" ON public.mitarbeiter_abteilung USING btree (abteilung_id);


--
-- Name: mitarbeiter_dokument__FKc2g6vg1xmyls40mfplx0kid67; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "mitarbeiter_dokument__FKc2g6vg1xmyls40mfplx0kid67" ON public.mitarbeiter_dokument USING btree (mitarbeiter_id);


--
-- Name: mitarbeiter_notiz__FK6lxmk16cci1suaujkgw14ouj4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "mitarbeiter_notiz__FK6lxmk16cci1suaujkgw14ouj4" ON public.mitarbeiter_notiz USING btree (mitarbeiter_id);


--
-- Name: mitarbeiter_stundenlohn__idx_stundenlohn_mitarbeiter_datum; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX mitarbeiter_stundenlohn__idx_stundenlohn_mitarbeiter_datum ON public.mitarbeiter_stundenlohn USING btree (mitarbeiter_id, gueltig_ab);


--
-- Name: mitarbeiter_stundenlohn__uk_stundenlohn_mitarbeiter_datum; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX mitarbeiter_stundenlohn__uk_stundenlohn_mitarbeiter_datum ON public.mitarbeiter_stundenlohn USING btree (mitarbeiter_id, gueltig_ab);


--
-- Name: monats_saldo__FK1iflfii6yokov9l2cmv36m6a3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "monats_saldo__FK1iflfii6yokov9l2cmv36m6a3" ON public.monats_saldo USING btree (festgeschrieben_von_mitarbeiter_id);


--
-- Name: monatsabschluss_audit__fk_monatsabschluss_audit_akteur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX monatsabschluss_audit__fk_monatsabschluss_audit_akteur ON public.monatsabschluss_audit USING btree (akteur_id);


--
-- Name: out_of_office_schedule__FKptqv7sqf2u8v5wky19f3uauu0; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "out_of_office_schedule__FKptqv7sqf2u8v5wky19f3uauu0" ON public.out_of_office_schedule USING btree (signature_id);


--
-- Name: produktkategorie__FKbtgb9v2fmjrsvd5pfpl27voty; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "produktkategorie__FKbtgb9v2fmjrsvd5pfpl27voty" ON public.produktkategorie USING btree (parent_kategorie_id);


--
-- Name: projekt__FKj0obnxobkta8gnjhn4hq6bdlt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt__FKj0obnxobkta8gnjhn4hq6bdlt" ON public.projekt USING btree (kunden_id);


--
-- Name: projekt_dokument__FKjkeyqk6vd8g3gd2uxxb7cdjwm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_dokument__FKjkeyqk6vd8g3gd2uxxb7cdjwm" ON public.projekt_dokument USING btree (lieferant_id);


--
-- Name: projekt_dokument__FKk12opivflsv1abgvokn1u56f3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_dokument__FKk12opivflsv1abgvokn1u56f3" ON public.projekt_dokument USING btree (uploaded_by_id);


--
-- Name: projekt_dokument__FKs3kbr4k2j7s0rmjlav1660el8; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_dokument__FKs3kbr4k2j7s0rmjlav1660el8" ON public.projekt_dokument USING btree (projekt);


--
-- Name: projekt_geschaeftsdokument__FK1lnans707maovpsgpkgxnyg84; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_geschaeftsdokument__FK1lnans707maovpsgpkgxnyg84" ON public.projekt_geschaeftsdokument USING btree (referenz_dokument_id);


--
-- Name: projekt_kunden_emails__FK7bt6ot4v6rget7ei5ur19u0y8; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_kunden_emails__FK7bt6ot4v6rget7ei5ur19u0y8" ON public.projekt_kunden_emails USING btree (projekt_id);


--
-- Name: projekt_notiz__FKl0rf54prfq2tjktp6svbe39wk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_notiz__FKl0rf54prfq2tjktp6svbe39wk" ON public.projekt_notiz USING btree (projekt_id);


--
-- Name: projekt_notiz__FKtco2khbngbo2vh7pmesx8cqbv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_notiz__FKtco2khbngbo2vh7pmesx8cqbv" ON public.projekt_notiz USING btree (mitarbeiter_id);


--
-- Name: projekt_notiz_bild__FKk561w9l0apru61989md1tgkeb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_notiz_bild__FKk561w9l0apru61989md1tgkeb" ON public.projekt_notiz_bild USING btree (notiz_id);


--
-- Name: projekt_produktkategorie__FK7cgq3q42get8289lvlxrncsrv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_produktkategorie__FK7cgq3q42get8289lvlxrncsrv" ON public.projekt_produktkategorie USING btree (produktkategorie_id);


--
-- Name: projekt_produktkategorie__FKkv1qwmsdlkwmmjsr8ky93nchk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "projekt_produktkategorie__FKkv1qwmsdlkwmmjsr8ky93nchk" ON public.projekt_produktkategorie USING btree (projekt_id);


--
-- Name: push_subscription__FK1nrjw1akxmtj9ftoy163x09l6; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "push_subscription__FK1nrjw1akxmtj9ftoy163x09l6" ON public.push_subscription USING btree (mitarbeiter_id);


--
-- Name: schnittbilder__FKb0r6fjh4pb8c0b44j1lbqtuca; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "schnittbilder__FKb0r6fjh4pb8c0b44j1lbqtuca" ON public.schnittbilder USING btree (kategorie_id);


--
-- Name: seen_sender_domain__idx_seen_sender_domain_first_seen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX seen_sender_domain__idx_seen_sender_domain_first_seen ON public.seen_sender_domain USING btree (first_seen);


--
-- Name: sprachnachricht__fk_sprachnachricht_abgehoert_von; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__fk_sprachnachricht_abgehoert_von ON public.sprachnachricht USING btree (abgehoert_von);


--
-- Name: sprachnachricht__fk_sprachnachricht_anruf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__fk_sprachnachricht_anruf ON public.sprachnachricht USING btree (anruf_id);


--
-- Name: sprachnachricht__fk_sprachnachricht_kunde; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__fk_sprachnachricht_kunde ON public.sprachnachricht USING btree (kunde_id);


--
-- Name: sprachnachricht__fk_sprachnachricht_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__fk_sprachnachricht_lieferant ON public.sprachnachricht USING btree (lieferant_id);


--
-- Name: sprachnachricht__fk_sprachnachricht_steuerberater; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__fk_sprachnachricht_steuerberater ON public.sprachnachricht USING btree (steuerberater_id);


--
-- Name: sprachnachricht__idx_sprachnachricht_nummer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__idx_sprachnachricht_nummer ON public.sprachnachricht USING btree (nummer_normalisiert);


--
-- Name: sprachnachricht__idx_sprachnachricht_zeitpunkt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sprachnachricht__idx_sprachnachricht_zeitpunkt ON public.sprachnachricht USING btree (zeitpunkt);


--
-- Name: sprachnachricht__uk_sprachnachricht; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sprachnachricht__uk_sprachnachricht ON public.sprachnachricht USING btree (anrufbeantworter, zeitpunkt, nummer_roh);


--
-- Name: steuerberater_ansprechpartner__idx_sb_ap_steuerberater; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX steuerberater_ansprechpartner__idx_sb_ap_steuerberater ON public.steuerberater_ansprechpartner USING btree (steuerberater_id);


--
-- Name: steuerberater_kontakt_emails__FKorfns4otta1ek0ivc3m05en5x; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "steuerberater_kontakt_emails__FKorfns4otta1ek0ivc3m05en5x" ON public.steuerberater_kontakt_emails USING btree (steuerberater_id);


--
-- Name: sv_satz__idx_sv_satz_typ; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sv_satz__idx_sv_satz_typ ON public.sv_satz USING btree (satz_typ);


--
-- Name: sv_satz__uk_sv_satz_typ_ab; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sv_satz__uk_sv_satz_typ_ab ON public.sv_satz USING btree (satz_typ, gueltig_ab);


--
-- Name: telefon_anruf__fk_telefon_anruf_kunde; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX telefon_anruf__fk_telefon_anruf_kunde ON public.telefon_anruf USING btree (kunde_id);


--
-- Name: telefon_anruf__fk_telefon_anruf_lieferant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX telefon_anruf__fk_telefon_anruf_lieferant ON public.telefon_anruf USING btree (lieferant_id);


--
-- Name: telefon_anruf__fk_telefon_anruf_steuerberater; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX telefon_anruf__fk_telefon_anruf_steuerberater ON public.telefon_anruf USING btree (steuerberater_id);


--
-- Name: telefon_anruf__idx_telefon_anruf_nummer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX telefon_anruf__idx_telefon_anruf_nummer ON public.telefon_anruf USING btree (nummer_normalisiert);


--
-- Name: telefon_anruf__idx_telefon_anruf_zeitpunkt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX telefon_anruf__idx_telefon_anruf_zeitpunkt ON public.telefon_anruf USING btree (zeitpunkt);


--
-- Name: telefon_anruf__uk_telefon_anruf; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX telefon_anruf__uk_telefon_anruf ON public.telefon_anruf USING btree (zeitpunkt, art, eigene_nummer, nummer_roh);


--
-- Name: textbaustein_dokumenttyp_enum__FK8hgp7s2lhidjwhjqtrbdbnche; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "textbaustein_dokumenttyp_enum__FK8hgp7s2lhidjwhjqtrbdbnche" ON public.textbaustein_dokumenttyp_enum USING btree (textbaustein_id);


--
-- Name: textbaustein_placeholder__FKqyjk8hutbj5qir19iifmhc20l; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "textbaustein_placeholder__FKqyjk8hutbj5qir19iifmhc20l" ON public.textbaustein_placeholder USING btree (textbaustein_id);


--
-- Name: urlaubsantrag__FK6pp00g1q8epx65rwnnx65bhg3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "urlaubsantrag__FK6pp00g1q8epx65rwnnx65bhg3" ON public.urlaubsantrag USING btree (mitarbeiter_id);


--
-- Name: verteilungsschluessel_eintrag__FKbou6nt4hmrpfuhh180vi2dmj4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "verteilungsschluessel_eintrag__FKbou6nt4hmrpfuhh180vi2dmj4" ON public.verteilungsschluessel_eintrag USING btree (verteilungsschluessel_id);


--
-- Name: verteilungsschluessel_eintrag__FKgks5yei7k4aan3bou6lar8yfi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "verteilungsschluessel_eintrag__FKgks5yei7k4aan3bou6lar8yfi" ON public.verteilungsschluessel_eintrag USING btree (mietpartei_id);


--
-- Name: verteilungsschluessel_eintrag__FKj41gn459lmkmx6pk5nsecvuxc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "verteilungsschluessel_eintrag__FKj41gn459lmkmx6pk5nsecvuxc" ON public.verteilungsschluessel_eintrag USING btree (verbrauchsgegenstand_id);


--
-- Name: zeitbuchung__FK4wicif5gtd7can3ge6kxtqe23; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FK4wicif5gtd7can3ge6kxtqe23" ON public.zeitbuchung USING btree (arbeitsgang_stundensatz_id);


--
-- Name: zeitbuchung__FK81s7ie1gywxfoti8ro5s9x1hx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FK81s7ie1gywxfoti8ro5s9x1hx" ON public.zeitbuchung USING btree (erfasst_von_mitarbeiter_id);


--
-- Name: zeitbuchung__FKaqk8irsk4nbauh3dhpr9qj4g6; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FKaqk8irsk4nbauh3dhpr9qj4g6" ON public.zeitbuchung USING btree (zuletzt_geaendert_von);


--
-- Name: zeitbuchung__FKc1e8i75jf31h6296qsrea9ync; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FKc1e8i75jf31h6296qsrea9ync" ON public.zeitbuchung USING btree (projekt_produktkategorie_id);


--
-- Name: zeitbuchung__FKc9bxldv2wcr3efhbt2ouymlxg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FKc9bxldv2wcr3efhbt2ouymlxg" ON public.zeitbuchung USING btree (projekt_id);


--
-- Name: zeitbuchung__FKfcgnpiwlh9u9ot845i7p48tmr; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung__FKfcgnpiwlh9u9ot845i7p48tmr" ON public.zeitbuchung USING btree (arbeitsgang_id);


--
-- Name: zeitbuchung__idx_zeitbuchung_automatisch_beendet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX zeitbuchung__idx_zeitbuchung_automatisch_beendet ON public.zeitbuchung USING btree (automatisch_beendet, start_zeit);


--
-- Name: zeitbuchung_audit__FKs9oi4hhr8g5di5jp6sw8srtb7; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitbuchung_audit__FKs9oi4hhr8g5di5jp6sw8srtb7" ON public.zeitbuchung_audit USING btree (geaendert_von_mitarbeiter_id);


--
-- Name: zeitkonto_korrektur__FK4302496ih5ju8b76ki504bjbg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitkonto_korrektur__FK4302496ih5ju8b76ki504bjbg" ON public.zeitkonto_korrektur USING btree (storniert_von_id);


--
-- Name: zeitkonto_korrektur__FKammh0xpx84b1wrom73awsqm7o; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitkonto_korrektur__FKammh0xpx84b1wrom73awsqm7o" ON public.zeitkonto_korrektur USING btree (mitarbeiter_id);


--
-- Name: zeitkonto_korrektur__FKfcrle0vb7ixmxfkapac3uf45i; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitkonto_korrektur__FKfcrle0vb7ixmxfkapac3uf45i" ON public.zeitkonto_korrektur USING btree (erstellt_von_id);


--
-- Name: zeitkonto_korrektur_audit__FK94ffmogll87bhfj6g3ufwpjxy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "zeitkonto_korrektur_audit__FK94ffmogll87bhfj6g3ufwpjxy" ON public.zeitkonto_korrektur_audit USING btree (geaendert_von_mitarbeiter_id);


--
-- Name: zeitkonto_version__fk_zeitkonto_version_vorlage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX zeitkonto_version__fk_zeitkonto_version_vorlage ON public.zeitkonto_version USING btree (vorlage_id);


--
-- Name: firmeninformation fk1cut60jy9u292yx9x339p43ay; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.firmeninformation
    ADD CONSTRAINT fk1cut60jy9u292yx9x339p43ay FOREIGN KEY (gewerk_id) REFERENCES public.gewerk(id);


--
-- Name: monats_saldo fk1iflfii6yokov9l2cmv36m6a3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monats_saldo
    ADD CONSTRAINT fk1iflfii6yokov9l2cmv36m6a3 FOREIGN KEY (festgeschrieben_von_mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: projekt_geschaeftsdokument fk1lnans707maovpsgpkgxnyg84; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_geschaeftsdokument
    ADD CONSTRAINT fk1lnans707maovpsgpkgxnyg84 FOREIGN KEY (referenz_dokument_id) REFERENCES public.projekt_geschaeftsdokument(id);


--
-- Name: push_subscription fk1nrjw1akxmtj9ftoy163x09l6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscription
    ADD CONSTRAINT fk1nrjw1akxmtj9ftoy163x09l6 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: artikel_hilfsstoffe fk239l3yk1x02byfley91p4idwf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_hilfsstoffe
    ADD CONSTRAINT fk239l3yk1x02byfley91p4idwf FOREIGN KEY (id) REFERENCES public.artikel(id);


--
-- Name: kalender_eintrag fk23nredk9cp87futr9wbd2w79u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT fk23nredk9cp87futr9wbd2w79u FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: anfrage_notiz_bild fk27l5iks86tywcvido9yxtv5e1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz_bild
    ADD CONSTRAINT fk27l5iks86tywcvido9yxtv5e1 FOREIGN KEY (notiz_id) REFERENCES public.anfrage_notiz(id);


--
-- Name: mitarbeiter fk28s44gxbnll5cni8jjse68nq1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter
    ADD CONSTRAINT fk28s44gxbnll5cni8jjse68nq1 FOREIGN KEY (krankenkasse_id) REFERENCES public.krankenkasse(id);


--
-- Name: abteilung_dokument_berechtigung fk2f0otqqmju7v9cwa45hplwapm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abteilung_dokument_berechtigung
    ADD CONSTRAINT fk2f0otqqmju7v9cwa45hplwapm FOREIGN KEY (abteilung_id) REFERENCES public.abteilung(id);


--
-- Name: lohnabrechnung fk2nhfk54qj9ltp98w3uu7h7yd2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lohnabrechnung
    ADD CONSTRAINT fk2nhfk54qj9ltp98w3uu7h7yd2 FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id);


--
-- Name: materialkosten fk334hjof01f74lut1bq6n8t5lo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materialkosten
    ADD CONSTRAINT fk334hjof01f74lut1bq6n8t5lo FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: email_attachment fk361t957grpy6abpe8stl7rips; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_attachment
    ADD CONSTRAINT fk361t957grpy6abpe8stl7rips FOREIGN KEY (lieferant_dokument_id) REFERENCES public.lieferant_dokument(id);


--
-- Name: artikel fk3eve2o35lujpa0um9h5kiakh1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel
    ADD CONSTRAINT fk3eve2o35lujpa0um9h5kiakh1 FOREIGN KEY (werkstoff_id) REFERENCES public.werkstoff(id);


--
-- Name: lieferant_bild fk3jsl58pjxxlgi2g1awao4wx5p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_bild
    ADD CONSTRAINT fk3jsl58pjxxlgi2g1awao4wx5p FOREIGN KEY (reklamation_id) REFERENCES public.lieferant_reklamation(id);


--
-- Name: langzeitkrankmeldung fk3qu3ox7cf3mrqfgnfdagca0f4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung
    ADD CONSTRAINT fk3qu3ox7cf3mrqfgnfdagca0f4 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: email fk40n6q5qbytkv2ts98itxdh2mb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT fk40n6q5qbytkv2ts98itxdh2mb FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: zeitkonto_korrektur fk4302496ih5ju8b76ki504bjbg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur
    ADD CONSTRAINT fk4302496ih5ju8b76ki504bjbg FOREIGN KEY (storniert_von_id) REFERENCES public.mitarbeiter(id);


--
-- Name: email fk4iets5h6ml4htxhh8r2cyg61v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT fk4iets5h6ml4htxhh8r2cyg61v FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: mitarbeiter_abteilung fk4mo0bw7rs0hulyfhqvfwledlv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_abteilung
    ADD CONSTRAINT fk4mo0bw7rs0hulyfhqvfwledlv FOREIGN KEY (abteilung_id) REFERENCES public.abteilung(id);


--
-- Name: verbrauchsgegenstand fk4pc1yhjue8o31xj4uc0t204gm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verbrauchsgegenstand
    ADD CONSTRAINT fk4pc1yhjue8o31xj4uc0t204gm FOREIGN KEY (raum_id) REFERENCES public.raum(id);


--
-- Name: projekt_geschaeftsdokument fk4pmmorcrh4u9c4l557t3l4ch; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_geschaeftsdokument
    ADD CONSTRAINT fk4pmmorcrh4u9c4l557t3l4ch FOREIGN KEY (id) REFERENCES public.projekt_dokument(id);


--
-- Name: ausgangs_geschaeftsdokument fk4tpqnu19710wax59ela3bkggm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT fk4tpqnu19710wax59ela3bkggm FOREIGN KEY (vorgaenger_id) REFERENCES public.ausgangs_geschaeftsdokument(id);


--
-- Name: zeitbuchung fk4wicif5gtd7can3ge6kxtqe23; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fk4wicif5gtd7can3ge6kxtqe23 FOREIGN KEY (arbeitsgang_stundensatz_id) REFERENCES public.arbeitsgang_stundensatz(id);


--
-- Name: lieferant_dokument_verknuepfung fk557cmofj4yh8o7v3iprjkrolx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung
    ADD CONSTRAINT fk557cmofj4yh8o7v3iprjkrolx FOREIGN KEY (verknuepft_id) REFERENCES public.lieferant_dokument(id);


--
-- Name: lieferant_dokument fk5fnimy797c9vpvc3si7h2ltsc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument
    ADD CONSTRAINT fk5fnimy797c9vpvc3si7h2ltsc FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: bwa_position fk5maeljbih33gg9i4sse6po1n5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_position
    ADD CONSTRAINT fk5maeljbih33gg9i4sse6po1n5 FOREIGN KEY (bwa_upload_id) REFERENCES public.bwa_upload(id);


--
-- Name: kostenposition fk5vo941bwtiwsi4sjr3eva5kwd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kostenposition
    ADD CONSTRAINT fk5vo941bwtiwsi4sjr3eva5kwd FOREIGN KEY (kostenstelle_id) REFERENCES public.miete_kostenstelle(id);


--
-- Name: bwa_upload fk66q12gi4xqo63u2c8ea7hco5w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_upload
    ADD CONSTRAINT fk66q12gi4xqo63u2c8ea7hco5w FOREIGN KEY (email_id) REFERENCES public.email(id);


--
-- Name: mitarbeiter_notiz fk6lxmk16cci1suaujkgw14ouj4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_notiz
    ADD CONSTRAINT fk6lxmk16cci1suaujkgw14ouj4 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: arbeitsgang fk6n88tt8b8w4r1opg8ernqera9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang
    ADD CONSTRAINT fk6n88tt8b8w4r1opg8ernqera9 FOREIGN KEY (abteilung_id) REFERENCES public.abteilung(id);


--
-- Name: urlaubsantrag fk6pp00g1q8epx65rwnnx65bhg3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.urlaubsantrag
    ADD CONSTRAINT fk6pp00g1q8epx65rwnnx65bhg3 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: artikel_in_projekt fk6tk7id8jx0b4jw05lqwr2k5es; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt
    ADD CONSTRAINT fk6tk7id8jx0b4jw05lqwr2k5es FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: anfrage fk74hhvjetu7xw9kbg0hnq4f94s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage
    ADD CONSTRAINT fk74hhvjetu7xw9kbg0hnq4f94s FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: projekt_kunden_emails fk7bt6ot4v6rget7ei5ur19u0y8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_kunden_emails
    ADD CONSTRAINT fk7bt6ot4v6rget7ei5ur19u0y8 FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: projekt_produktkategorie fk7cgq3q42get8289lvlxrncsrv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_produktkategorie
    ADD CONSTRAINT fk7cgq3q42get8289lvlxrncsrv FOREIGN KEY (produktkategorie_id) REFERENCES public.produktkategorie(id);


--
-- Name: leistung fk7hofs5fopj2sk601jk5m69nse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leistung
    ADD CONSTRAINT fk7hofs5fopj2sk601jk5m69nse FOREIGN KEY (kategorie_id) REFERENCES public.produktkategorie(id);


--
-- Name: lieferant_geschaeftsdokument fk7mhykqqkvutp5xn2kq1nvte10; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_geschaeftsdokument
    ADD CONSTRAINT fk7mhykqqkvutp5xn2kq1nvte10 FOREIGN KEY (id) REFERENCES public.lieferant_dokument(id);


--
-- Name: lieferanten_emails fk7n9pw9uq6twnqk2gvohrasjqp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_emails
    ADD CONSTRAINT fk7n9pw9uq6twnqk2gvohrasjqp FOREIGN KEY (lieferanten_id) REFERENCES public.lieferanten(id);


--
-- Name: kategorie fk7rsp0w04q1kipndasbaxkhds9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategorie
    ADD CONSTRAINT fk7rsp0w04q1kipndasbaxkhds9 FOREIGN KEY (parent_kategorie_id) REFERENCES public.kategorie(id);


--
-- Name: anfrage fk7tcxpwd7j1238baugoddm2c6c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage
    ADD CONSTRAINT fk7tcxpwd7j1238baugoddm2c6c FOREIGN KEY (kunde_id) REFERENCES public.kunde(id);


--
-- Name: zeitbuchung fk81s7ie1gywxfoti8ro5s9x1hx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fk81s7ie1gywxfoti8ro5s9x1hx FOREIGN KEY (erfasst_von_mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: kalender_eintrag fk825cvevrtryta7qgjmr138jt0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT fk825cvevrtryta7qgjmr138jt0 FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: anfrage_notiz fk8b3ncalux4oq9ry9repssbnfs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz
    ADD CONSTRAINT fk8b3ncalux4oq9ry9repssbnfs FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: lieferant_dokument_projekt_anteil fk8e8msmb5hg51mtnyy9wjdgyve; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil
    ADD CONSTRAINT fk8e8msmb5hg51mtnyy9wjdgyve FOREIGN KEY (zugeordnet_von_user_id) REFERENCES public.frontend_user_profile(id);


--
-- Name: textbaustein_dokumenttyp_enum fk8hgp7s2lhidjwhjqtrbdbnche; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.textbaustein_dokumenttyp_enum
    ADD CONSTRAINT fk8hgp7s2lhidjwhjqtrbdbnche FOREIGN KEY (textbaustein_id) REFERENCES public.textbaustein(id);


--
-- Name: zaehlerstand fk8hi5ig6jb6uqsli155pwep3lh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zaehlerstand
    ADD CONSTRAINT fk8hi5ig6jb6uqsli155pwep3lh FOREIGN KEY (verbrauchsgegenstand_id) REFERENCES public.verbrauchsgegenstand(id);


--
-- Name: frontend_user_profile fk8hy25aovgdk9kjt68i60ysan7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT fk8hy25aovgdk9kjt68i60ysan7 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: email fk8nttd3ghg0jdhfmcr9o1ow65s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT fk8nttd3ghg0jdhfmcr9o1ow65s FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id);


--
-- Name: zeitkonto_korrektur_audit fk94ffmogll87bhfj6g3ufwpjxy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur_audit
    ADD CONSTRAINT fk94ffmogll87bhfj6g3ufwpjxy FOREIGN KEY (geaendert_von_mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: artikel_werkstoffe fk94rsucrfvxc8c0usier6dsj3q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_werkstoffe
    ADD CONSTRAINT fk94rsucrfvxc8c0usier6dsj3q FOREIGN KEY (id) REFERENCES public.artikel(id);


--
-- Name: monats_saldo fk9hw3giv6y9wiyunwnwdo3tjgf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monats_saldo
    ADD CONSTRAINT fk9hw3giv6y9wiyunwnwdo3tjgf FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: arbeitsgang_stundensatz fk9n1dvl3im1swo1dw4ctybcs41; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arbeitsgang_stundensatz
    ADD CONSTRAINT fk9n1dvl3im1swo1dw4ctybcs41 FOREIGN KEY (arbeitsgang_id) REFERENCES public.arbeitsgang(id);


--
-- Name: lieferanten_artikel_preise fk9p4q5hy21v564dw1dpwpmy1t2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_artikel_preise
    ADD CONSTRAINT fk9p4q5hy21v564dw1dpwpmy1t2 FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: lieferant_dokument_projekt_anteil fk9s9oi3un5efau5swc1kcvq708; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil
    ADD CONSTRAINT fk9s9oi3un5efau5swc1kcvq708 FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: bwa_upload fk9t7wn2ad5lclb957icluomdt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_upload
    ADD CONSTRAINT fk9t7wn2ad5lclb957icluomdt FOREIGN KEY (freigegeben_von_id) REFERENCES public.mitarbeiter(id);


--
-- Name: lohnabrechnung fk9yhpq6453cxflovfab1p5nsxa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lohnabrechnung
    ADD CONSTRAINT fk9yhpq6453cxflovfab1p5nsxa FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: artikel_in_projekt fk_aip_preisstand; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt
    ADD CONSTRAINT fk_aip_preisstand FOREIGN KEY (lieferanten_artikel_preis_id) REFERENCES public.lieferanten_artikel_preise(id) ON DELETE SET NULL;


--
-- Name: beleg fk_beleg_ausgangsrechnung; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fk_beleg_ausgangsrechnung FOREIGN KEY (ausgangsrechnung_id) REFERENCES public.projekt_geschaeftsdokument(id) ON DELETE SET NULL;


--
-- Name: beleg fk_beleg_lieferant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fk_beleg_lieferant FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id) ON DELETE SET NULL;


--
-- Name: beleg_position fk_beleg_position_beleg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_position
    ADD CONSTRAINT fk_beleg_position_beleg FOREIGN KEY (beleg_id) REFERENCES public.beleg(id) ON DELETE CASCADE;


--
-- Name: beleg fk_beleg_sachkonto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fk_beleg_sachkonto FOREIGN KEY (sachkonto_id) REFERENCES public.sachkonto(id) ON DELETE SET NULL;


--
-- Name: beleg fk_beleg_uploaded_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fk_beleg_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES public.mitarbeiter(id) ON DELETE SET NULL;


--
-- Name: beleg fk_beleg_validiert_von; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fk_beleg_validiert_von FOREIGN KEY (validiert_von_id) REFERENCES public.mitarbeiter(id) ON DELETE SET NULL;


--
-- Name: beleg_kostenstellen_anteil fk_bka_beleg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_kostenstellen_anteil
    ADD CONSTRAINT fk_bka_beleg FOREIGN KEY (beleg_id) REFERENCES public.beleg(id) ON DELETE CASCADE;


--
-- Name: datev_personalnummer fk_datev_personalnummer_mitarbeiter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datev_personalnummer
    ADD CONSTRAINT fk_datev_personalnummer_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: email_draft fk_draft_anfrage; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft
    ADD CONSTRAINT fk_draft_anfrage FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id) ON DELETE SET NULL;


--
-- Name: email_draft fk_draft_projekt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft
    ADD CONSTRAINT fk_draft_projekt FOREIGN KEY (projekt_id) REFERENCES public.projekt(id) ON DELETE SET NULL;


--
-- Name: email_draft fk_draft_reply_email; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft
    ADD CONSTRAINT fk_draft_reply_email FOREIGN KEY (reply_email_id) REFERENCES public.email(id) ON DELETE SET NULL;


--
-- Name: email_draft_attachment fk_email_draft_attachment_draft; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_draft_attachment
    ADD CONSTRAINT fk_email_draft_attachment_draft FOREIGN KEY (draft_id) REFERENCES public.email_draft(id) ON DELETE CASCADE;


--
-- Name: entity_last_accessed fk_entity_last_accessed_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_last_accessed
    ADD CONSTRAINT fk_entity_last_accessed_user FOREIGN KEY (user_id) REFERENCES public.frontend_user_profile(id) ON DELETE CASCADE;


--
-- Name: formular_template_textbaustein_default fk_fttd_textbaustein; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_textbaustein_default
    ADD CONSTRAINT fk_fttd_textbaustein FOREIGN KEY (textbaustein_id) REFERENCES public.textbaustein(id) ON DELETE CASCADE;


--
-- Name: kategorie_rollen fk_kategorie_rollen_kategorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategorie_rollen
    ADD CONSTRAINT fk_kategorie_rollen_kategorie FOREIGN KEY (kategorie_id) REFERENCES public.kategorie(id) ON DELETE CASCADE;


--
-- Name: kontakt_rufnummer fk_kontakt_rufnummer_kunde; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kontakt_rufnummer
    ADD CONSTRAINT fk_kontakt_rufnummer_kunde FOREIGN KEY (kunde_id) REFERENCES public.kunde(id) ON DELETE CASCADE;


--
-- Name: kontakt_rufnummer fk_kontakt_rufnummer_lieferant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kontakt_rufnummer
    ADD CONSTRAINT fk_kontakt_rufnummer_lieferant FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id) ON DELETE CASCADE;


--
-- Name: kontakt_rufnummer fk_kontakt_rufnummer_steuerberater; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kontakt_rufnummer
    ADD CONSTRAINT fk_kontakt_rufnummer_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id) ON DELETE CASCADE;


--
-- Name: kunde_notiz fk_kunde_notiz_kunde; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunde_notiz
    ADD CONSTRAINT fk_kunde_notiz_kunde FOREIGN KEY (kunde_id) REFERENCES public.kunde(id) ON DELETE CASCADE;


--
-- Name: langzeitkrankmeldung_phase fk_langzeitkrankmeldung_phase_meldung; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.langzeitkrankmeldung_phase
    ADD CONSTRAINT fk_langzeitkrankmeldung_phase_meldung FOREIGN KEY (langzeitkrankmeldung_id) REFERENCES public.langzeitkrankmeldung(id) ON DELETE CASCADE;


--
-- Name: lieferant_dokument_position fk_ld_position_geschaeftsdokument; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_position
    ADD CONSTRAINT fk_ld_position_geschaeftsdokument FOREIGN KEY (geschaeftsdokument_id) REFERENCES public.lieferant_geschaeftsdokument(id) ON DELETE CASCADE;


--
-- Name: lieferant_dokument_position fk_ld_position_kostenstelle; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_position
    ADD CONSTRAINT fk_ld_position_kostenstelle FOREIGN KEY (kostenstelle_id) REFERENCES public.firma_kostenstelle(id) ON DELETE SET NULL;


--
-- Name: lieferant_dokument_position fk_ld_position_projekt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_position
    ADD CONSTRAINT fk_ld_position_projekt FOREIGN KEY (projekt_id) REFERENCES public.projekt(id) ON DELETE SET NULL;


--
-- Name: lieferant_dokument_verknuepfung_gesperrt fk_ld_verkn_gesperrt_dokument; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung_gesperrt
    ADD CONSTRAINT fk_ld_verkn_gesperrt_dokument FOREIGN KEY (dokument_id) REFERENCES public.lieferant_dokument(id) ON DELETE CASCADE;


--
-- Name: lieferant_dokument_verknuepfung_gesperrt fk_ld_verkn_gesperrt_verknuepft; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung_gesperrt
    ADD CONSTRAINT fk_ld_verkn_gesperrt_verknuepft FOREIGN KEY (verknuepft_id) REFERENCES public.lieferant_dokument(id) ON DELETE CASCADE;


--
-- Name: lieferanten_rollen fk_lieferanten_rollen_lieferant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_rollen
    ADD CONSTRAINT fk_lieferanten_rollen_lieferant FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id) ON DELETE CASCADE;


--
-- Name: ooo_reply_log fk_ooo_reply_log_schedule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ooo_reply_log
    ADD CONSTRAINT fk_ooo_reply_log_schedule FOREIGN KEY (schedule_id) REFERENCES public.out_of_office_schedule(id) ON DELETE CASCADE;


--
-- Name: steuerberater_ansprechpartner fk_sb_ap_steuerberater; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_ansprechpartner
    ADD CONSTRAINT fk_sb_ap_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id) ON DELETE CASCADE;


--
-- Name: sprachnachricht fk_sprachnachricht_abgehoert_von; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT fk_sprachnachricht_abgehoert_von FOREIGN KEY (abgehoert_von) REFERENCES public.frontend_user_profile(id) ON DELETE SET NULL;


--
-- Name: sprachnachricht fk_sprachnachricht_anruf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT fk_sprachnachricht_anruf FOREIGN KEY (anruf_id) REFERENCES public.telefon_anruf(id) ON DELETE SET NULL;


--
-- Name: sprachnachricht fk_sprachnachricht_kunde; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT fk_sprachnachricht_kunde FOREIGN KEY (kunde_id) REFERENCES public.kunde(id) ON DELETE SET NULL;


--
-- Name: sprachnachricht fk_sprachnachricht_lieferant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT fk_sprachnachricht_lieferant FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id) ON DELETE SET NULL;


--
-- Name: sprachnachricht fk_sprachnachricht_steuerberater; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprachnachricht
    ADD CONSTRAINT fk_sprachnachricht_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id) ON DELETE SET NULL;


--
-- Name: mitarbeiter_stundenlohn fk_stundenlohn_mitarbeiter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_stundenlohn
    ADD CONSTRAINT fk_stundenlohn_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id) ON DELETE CASCADE;


--
-- Name: telefon_anruf fk_telefon_anruf_kunde; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telefon_anruf
    ADD CONSTRAINT fk_telefon_anruf_kunde FOREIGN KEY (kunde_id) REFERENCES public.kunde(id) ON DELETE SET NULL;


--
-- Name: telefon_anruf fk_telefon_anruf_lieferant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telefon_anruf
    ADD CONSTRAINT fk_telefon_anruf_lieferant FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id) ON DELETE SET NULL;


--
-- Name: telefon_anruf fk_telefon_anruf_steuerberater; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telefon_anruf
    ADD CONSTRAINT fk_telefon_anruf_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id) ON DELETE SET NULL;


--
-- Name: zeitkonto_pause fka4hvn22kgve0fu4523i1di80t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_pause
    ADD CONSTRAINT fka4hvn22kgve0fu4523i1di80t FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: kostenposition fkaaog4r7chwdxf5igxppd7owwj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kostenposition
    ADD CONSTRAINT fkaaog4r7chwdxf5igxppd7owwj FOREIGN KEY (verteilungsschluessel_id) REFERENCES public.verteilungsschluessel(id);


--
-- Name: artikel fkab40tv5rnynmyk9fk0uu1o24r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel
    ADD CONSTRAINT fkab40tv5rnynmyk9fk0uu1o24r FOREIGN KEY (kategorie_id) REFERENCES public.kategorie(id);


--
-- Name: zeitkonto_korrektur fkammh0xpx84b1wrom73awsqm7o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur
    ADD CONSTRAINT fkammh0xpx84b1wrom73awsqm7o FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: zeitbuchung fkaqk8irsk4nbauh3dhpr9qj4g6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fkaqk8irsk4nbauh3dhpr9qj4g6 FOREIGN KEY (zuletzt_geaendert_von) REFERENCES public.mitarbeiter(id);


--
-- Name: beleg_kostenstellen_anteil fkay67yyb32g8l8bqrpsnt75cvn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_kostenstellen_anteil
    ADD CONSTRAINT fkay67yyb32g8l8bqrpsnt75cvn FOREIGN KEY (kostenstelle_id) REFERENCES public.firma_kostenstelle(id);


--
-- Name: schnittbilder fkb0r6fjh4pb8c0b44j1lbqtuca; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schnittbilder
    ADD CONSTRAINT fkb0r6fjh4pb8c0b44j1lbqtuca FOREIGN KEY (kategorie_id) REFERENCES public.kategorie(id);


--
-- Name: artikel_in_projekt fkbff4hugr1a1yb7wpntofif4lj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt
    ADD CONSTRAINT fkbff4hugr1a1yb7wpntofif4lj FOREIGN KEY (artikel_id) REFERENCES public.artikel(id) ON DELETE CASCADE;


--
-- Name: verteilungsschluessel_eintrag fkbou6nt4hmrpfuhh180vi2dmj4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel_eintrag
    ADD CONSTRAINT fkbou6nt4hmrpfuhh180vi2dmj4 FOREIGN KEY (verteilungsschluessel_id) REFERENCES public.verteilungsschluessel(id);


--
-- Name: produktkategorie fkbtgb9v2fmjrsvd5pfpl27voty; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produktkategorie
    ADD CONSTRAINT fkbtgb9v2fmjrsvd5pfpl27voty FOREIGN KEY (parent_kategorie_id) REFERENCES public.produktkategorie(id);


--
-- Name: zeitbuchung fkc1e8i75jf31h6296qsrea9ync; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fkc1e8i75jf31h6296qsrea9ync FOREIGN KEY (projekt_produktkategorie_id) REFERENCES public.projekt_produktkategorie(id);


--
-- Name: mitarbeiter_dokument fkc2g6vg1xmyls40mfplx0kid67; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_dokument
    ADD CONSTRAINT fkc2g6vg1xmyls40mfplx0kid67 FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: zeitbuchung fkc9bxldv2wcr3efhbt2ouymlxg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fkc9bxldv2wcr3efhbt2ouymlxg FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: artikel_in_projekt fkcntoko2hycpioejmu3pjx9wv9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_in_projekt
    ADD CONSTRAINT fkcntoko2hycpioejmu3pjx9wv9 FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: monatsabschluss_audit fkco34qjc8k7b9svxrp1ny7fvcj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monatsabschluss_audit
    ADD CONSTRAINT fkco34qjc8k7b9svxrp1ny7fvcj FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: raum fkd4ykw5sigm2uxqlsg497sqruk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raum
    ADD CONSTRAINT fkd4ykw5sigm2uxqlsg497sqruk FOREIGN KEY (mietobjekt_id) REFERENCES public.mietobjekt(id);


--
-- Name: kasse_einstellung fkd57u63xyso5uqs98gd584naq9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kasse_einstellung
    ADD CONSTRAINT fkd57u63xyso5uqs98gd584naq9 FOREIGN KEY (privateinlage_sachkonto_id) REFERENCES public.sachkonto(id);


--
-- Name: lieferant_bild fkdmj0fo47ntl2ckj3m9k5j6vgw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_bild
    ADD CONSTRAINT fkdmj0fo47ntl2ckj3m9k5j6vgw FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: miete_kostenstelle fkds25jf6f8jo0wa3nmptgcnuv0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.miete_kostenstelle
    ADD CONSTRAINT fkds25jf6f8jo0wa3nmptgcnuv0 FOREIGN KEY (standard_schluessel_id) REFERENCES public.verteilungsschluessel(id);


--
-- Name: zeitkonto_version fkds3867swsphappa5o0o017nix; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_version
    ADD CONSTRAINT fkds3867swsphappa5o0o017nix FOREIGN KEY (vorlage_id) REFERENCES public.zeitkontenmodell(id);


--
-- Name: anfrage_notiz fkdv65icmxtwcf1s2e5j63au1af; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_notiz
    ADD CONSTRAINT fkdv65icmxtwcf1s2e5j63au1af FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: email fkdxywpbb3i2uhx6n5e4jpv2a9b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT fkdxywpbb3i2uhx6n5e4jpv2a9b FOREIGN KEY (parent_email_id) REFERENCES public.email(id);


--
-- Name: lieferant_reklamation fkf4w70mefnf5c696mwhr3o07wy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_reklamation
    ADD CONSTRAINT fkf4w70mefnf5c696mwhr3o07wy FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: zeitbuchung fkfcgnpiwlh9u9ot845i7p48tmr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fkfcgnpiwlh9u9ot845i7p48tmr FOREIGN KEY (arbeitsgang_id) REFERENCES public.arbeitsgang(id);


--
-- Name: zeitkonto_korrektur fkfcrle0vb7ixmxfkapac3uf45i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_korrektur
    ADD CONSTRAINT fkfcrle0vb7ixmxfkapac3uf45i FOREIGN KEY (erstellt_von_id) REFERENCES public.mitarbeiter(id);


--
-- Name: kalender_eintrag fkfcwj8kcgklypce1r1miuo2g0m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT fkfcwj8kcgklypce1r1miuo2g0m FOREIGN KEY (ersteller_id) REFERENCES public.mitarbeiter(id);


--
-- Name: kalender_eintrag fkfqbhhs48w4fnvr9e4fir9aoat; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT fkfqbhhs48w4fnvr9e4fir9aoat FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: lieferant_dokument_verknuepfung fkfuu9q0mrj8q3c4rc72h41nb6o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_verknuepfung
    ADD CONSTRAINT fkfuu9q0mrj8q3c4rc72h41nb6o FOREIGN KEY (dokument_id) REFERENCES public.lieferant_dokument(id);


--
-- Name: lieferant_dokument fkg21j111exuttor4np4xu4ot24; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument
    ADD CONSTRAINT fkg21j111exuttor4np4xu4ot24 FOREIGN KEY (uploaded_by_id) REFERENCES public.mitarbeiter(id);


--
-- Name: materialkosten fkgbrmg6ai07b0n46ff48knxx7c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materialkosten
    ADD CONSTRAINT fkgbrmg6ai07b0n46ff48knxx7c FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: verteilungsschluessel_eintrag fkgks5yei7k4aan3bou6lar8yfi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel_eintrag
    ADD CONSTRAINT fkgks5yei7k4aan3bou6lar8yfi FOREIGN KEY (mietpartei_id) REFERENCES public.mietpartei(id);


--
-- Name: lieferanten fkgopne9hwl7n945f7ghdqc21mw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten
    ADD CONSTRAINT fkgopne9hwl7n945f7ghdqc21mw FOREIGN KEY (standard_kostenstelle_id) REFERENCES public.firma_kostenstelle(id);


--
-- Name: frontend_user_profile fkgy9nu6luuoih1woj77ivgqyoi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT fkgy9nu6luuoih1woj77ivgqyoi FOREIGN KEY (email_absender_id) REFERENCES public.email_absender(id);


--
-- Name: zeitkonto_version fkhff50uev2ny69v9u2y3t310rn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitkonto_version
    ADD CONSTRAINT fkhff50uev2ny69v9u2y3t310rn FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: lieferant_reklamation fki0vq2dxmy7twmr91rl4m9eq9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_reklamation
    ADD CONSTRAINT fki0vq2dxmy7twmr91rl4m9eq9 FOREIGN KEY (lieferschein_id) REFERENCES public.lieferant_dokument(id);


--
-- Name: kalender_eintrag fkiulrt6wgh5ykpy8ejpl2j80ev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag
    ADD CONSTRAINT fkiulrt6wgh5ykpy8ejpl2j80ev FOREIGN KEY (kunde_id) REFERENCES public.kunde(id);


--
-- Name: monatsabschluss_audit fkixt6u94qy4w6n97hyswoy2o7k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monatsabschluss_audit
    ADD CONSTRAINT fkixt6u94qy4w6n97hyswoy2o7k FOREIGN KEY (akteur_id) REFERENCES public.mitarbeiter(id);


--
-- Name: projekt fkj0obnxobkta8gnjhn4hq6bdlt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt
    ADD CONSTRAINT fkj0obnxobkta8gnjhn4hq6bdlt FOREIGN KEY (kunden_id) REFERENCES public.kunde(id);


--
-- Name: anfrage_geschaeftsdokument fkj3cokssp02w7swpoypkt5jv95; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_geschaeftsdokument
    ADD CONSTRAINT fkj3cokssp02w7swpoypkt5jv95 FOREIGN KEY (id) REFERENCES public.anfrage_dokument(id);


--
-- Name: verteilungsschluessel_eintrag fkj41gn459lmkmx6pk5nsecvuxc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel_eintrag
    ADD CONSTRAINT fkj41gn459lmkmx6pk5nsecvuxc FOREIGN KEY (verbrauchsgegenstand_id) REFERENCES public.verbrauchsgegenstand(id);


--
-- Name: projekt_dokument fkjkeyqk6vd8g3gd2uxxb7cdjwm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument
    ADD CONSTRAINT fkjkeyqk6vd8g3gd2uxxb7cdjwm FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: bwa_position fkjkmp1yibx9emk2fh51t9stkvh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_position
    ADD CONSTRAINT fkjkmp1yibx9emk2fh51t9stkvh FOREIGN KEY (kostenstelle_id) REFERENCES public.firma_kostenstelle(id);


--
-- Name: projekt_dokument fkk12opivflsv1abgvokn1u56f3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument
    ADD CONSTRAINT fkk12opivflsv1abgvokn1u56f3 FOREIGN KEY (uploaded_by_id) REFERENCES public.mitarbeiter(id);


--
-- Name: projekt_notiz_bild fkk561w9l0apru61989md1tgkeb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz_bild
    ADD CONSTRAINT fkk561w9l0apru61989md1tgkeb FOREIGN KEY (notiz_id) REFERENCES public.projekt_notiz(id);


--
-- Name: anfrage_dokument fkkakl5w3pite8krxx0qo896ov4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_dokument
    ADD CONSTRAINT fkkakl5w3pite8krxx0qo896ov4 FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: email fkko6srmhjms5dem9hnbvp3lev2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email
    ADD CONSTRAINT fkko6srmhjms5dem9hnbvp3lev2 FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: projekt_produktkategorie fkkv1qwmsdlkwmmjsr8ky93nchk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_produktkategorie
    ADD CONSTRAINT fkkv1qwmsdlkwmmjsr8ky93nchk FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: lieferant_dokument_projekt_anteil fkkxmiv0ydwaea4rsafh2sqgy7m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil
    ADD CONSTRAINT fkkxmiv0ydwaea4rsafh2sqgy7m FOREIGN KEY (dokument_id) REFERENCES public.lieferant_dokument(id);


--
-- Name: projekt_notiz fkl0rf54prfq2tjktp6svbe39wk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz
    ADD CONSTRAINT fkl0rf54prfq2tjktp6svbe39wk FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: ausgangs_geschaeftsdokument fklgtye9xh70mbopblv0t2p5u17; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT fklgtye9xh70mbopblv0t2p5u17 FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: frontend_user_profile_role fkljm8yrdwbcil1a6ee4iairxtw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile_role
    ADD CONSTRAINT fkljm8yrdwbcil1a6ee4iairxtw FOREIGN KEY (frontend_user_profile_id) REFERENCES public.frontend_user_profile(id);


--
-- Name: ausgangs_geschaeftsdokument fklllmb6a21xp4buuykrebpws5i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT fklllmb6a21xp4buuykrebpws5i FOREIGN KEY (kunde_id) REFERENCES public.kunde(id);


--
-- Name: kalender_eintrag_teilnehmer fkm0jouf8mkfhwu9bttn3rjji1m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag_teilnehmer
    ADD CONSTRAINT fkm0jouf8mkfhwu9bttn3rjji1m FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: verteilungsschluessel fkmka8um8ln8mhd1sbt8w8lcqqh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verteilungsschluessel
    ADD CONSTRAINT fkmka8um8ln8mhd1sbt8w8lcqqh FOREIGN KEY (mietobjekt_id) REFERENCES public.mietobjekt(id);


--
-- Name: bwa_upload fkmwf1exgkseq7yksx2j4xrr551; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bwa_upload
    ADD CONSTRAINT fkmwf1exgkseq7yksx2j4xrr551 FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id);


--
-- Name: ausgangs_geschaeftsdokument fkn2517m61de0rysg7uokfa8ac7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT fkn2517m61de0rysg7uokfa8ac7 FOREIGN KEY (projekt_id) REFERENCES public.projekt(id);


--
-- Name: lieferant_dokument fkn3js7qygrabjp3p8v9dvnrmoa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument
    ADD CONSTRAINT fkn3js7qygrabjp3p8v9dvnrmoa FOREIGN KEY (beleg_id) REFERENCES public.beleg(id);


--
-- Name: lieferant_bild fknbar2m456dfal5qvyr7i3lu1q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_bild
    ADD CONSTRAINT fknbar2m456dfal5qvyr7i3lu1q FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: lieferant_notiz fkny9ewg3u4n84j2n9452ojfn3c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_notiz
    ADD CONSTRAINT fkny9ewg3u4n84j2n9452ojfn3c FOREIGN KEY (lieferant_id) REFERENCES public.lieferanten(id);


--
-- Name: lieferant_dokument fko26ih6io5iveeuwsq7vykh08o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument
    ADD CONSTRAINT fko26ih6io5iveeuwsq7vykh08o FOREIGN KEY (attachment_id) REFERENCES public.email_attachment(id);


--
-- Name: lieferant_dokument_projekt_anteil fko7j3pr0m7md9gh6cmi3iaxmnf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_dokument_projekt_anteil
    ADD CONSTRAINT fko7j3pr0m7md9gh6cmi3iaxmnf FOREIGN KEY (kostenstelle_id) REFERENCES public.firma_kostenstelle(id);


--
-- Name: kunden_emails fkocfqfi03pyffpsruvkng044x9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kunden_emails
    ADD CONSTRAINT fkocfqfi03pyffpsruvkng044x9 FOREIGN KEY (kunden_id) REFERENCES public.kunde(id);


--
-- Name: beleg fkopjxnuee5uig8gfkcx3kmp8k6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg
    ADD CONSTRAINT fkopjxnuee5uig8gfkcx3kmp8k6 FOREIGN KEY (kostenstelle_id) REFERENCES public.firma_kostenstelle(id);


--
-- Name: steuerberater_kontakt_emails fkorfns4otta1ek0ivc3m05en5x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.steuerberater_kontakt_emails
    ADD CONSTRAINT fkorfns4otta1ek0ivc3m05en5x FOREIGN KEY (steuerberater_id) REFERENCES public.steuerberater_kontakt(id);


--
-- Name: abwesenheit fkp1m4m9m370l0ekoohchtq9p95; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT fkp1m4m9m370l0ekoohchtq9p95 FOREIGN KEY (langzeitkrankmeldung_id) REFERENCES public.langzeitkrankmeldung(id);


--
-- Name: mitarbeiter_abteilung fkp3306e0e54tyw98r0tqhp9qae; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mitarbeiter_abteilung
    ADD CONSTRAINT fkp3306e0e54tyw98r0tqhp9qae FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: lieferanten_artikel_preise fkpduh3vh6vf524fyjqmtdf0i3p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferanten_artikel_preise
    ADD CONSTRAINT fkpduh3vh6vf524fyjqmtdf0i3p FOREIGN KEY (artikel_id) REFERENCES public.artikel(id) ON DELETE CASCADE;


--
-- Name: email_signature_image fkpfwt7v1tmth3t6xp79fip4du8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_signature_image
    ADD CONSTRAINT fkpfwt7v1tmth3t6xp79fip4du8 FOREIGN KEY (signature_id) REFERENCES public.email_signature(id);


--
-- Name: anfrage_kunden_emails fkpinl3l31eh8d690d23q86g88s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.anfrage_kunden_emails
    ADD CONSTRAINT fkpinl3l31eh8d690d23q86g88s FOREIGN KEY (anfrage_id) REFERENCES public.anfrage(id);


--
-- Name: mietpartei fkpo7mpkdxv05wxnqxaghkl5uns; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mietpartei
    ADD CONSTRAINT fkpo7mpkdxv05wxnqxaghkl5uns FOREIGN KEY (mietobjekt_id) REFERENCES public.mietobjekt(id);


--
-- Name: out_of_office_schedule fkptqv7sqf2u8v5wky19f3uauu0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.out_of_office_schedule
    ADD CONSTRAINT fkptqv7sqf2u8v5wky19f3uauu0 FOREIGN KEY (signature_id) REFERENCES public.email_signature(id);


--
-- Name: abwesenheit fkq2767pce39mwtcrdj3klfwx6d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT fkq2767pce39mwtcrdj3klfwx6d FOREIGN KEY (urlaubsantrag_id) REFERENCES public.urlaubsantrag(id);


--
-- Name: abwesenheit fkq6lqpw7ebfgi26b3x0c6ihksy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT fkq6lqpw7ebfgi26b3x0c6ihksy FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: artikel_dokument fkqfl8g6ssmq6o9wkmaj3kjwb2k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artikel_dokument
    ADD CONSTRAINT fkqfl8g6ssmq6o9wkmaj3kjwb2k FOREIGN KEY (artikel_id) REFERENCES public.artikel(id) ON DELETE CASCADE;


--
-- Name: kalender_eintrag_teilnehmer fkqlfi7d92htoye3w09akhb1xtd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kalender_eintrag_teilnehmer
    ADD CONSTRAINT fkqlfi7d92htoye3w09akhb1xtd FOREIGN KEY (kalender_eintrag_id) REFERENCES public.kalender_eintrag(id);


--
-- Name: ausgangs_geschaeftsdokument fkqllc9cls1j4n5ccy55faye7l9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ausgangs_geschaeftsdokument
    ADD CONSTRAINT fkqllc9cls1j4n5ccy55faye7l9 FOREIGN KEY (erstellt_von_id) REFERENCES public.frontend_user_profile(id);


--
-- Name: email_attachment fkqxylawa4l8ipoxstne8bdtghs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_attachment
    ADD CONSTRAINT fkqxylawa4l8ipoxstne8bdtghs FOREIGN KEY (email_id) REFERENCES public.email(id);


--
-- Name: textbaustein_placeholder fkqyjk8hutbj5qir19iifmhc20l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.textbaustein_placeholder
    ADD CONSTRAINT fkqyjk8hutbj5qir19iifmhc20l FOREIGN KEY (textbaustein_id) REFERENCES public.textbaustein(id);


--
-- Name: frontend_user_profile fkr832ttbmukk4sdbp46wmdmpvv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.frontend_user_profile
    ADD CONSTRAINT fkr832ttbmukk4sdbp46wmdmpvv FOREIGN KEY (default_signature_id) REFERENCES public.email_signature(id);


--
-- Name: formular_template_assignment fkrqvgxham8n6b54ep6ybi1bx0a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formular_template_assignment
    ADD CONSTRAINT fkrqvgxham8n6b54ep6ybi1bx0a FOREIGN KEY (user_id) REFERENCES public.frontend_user_profile(id);


--
-- Name: lohnabrechnung fkrvbnxfacldipkbyrkt9w8ylex; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lohnabrechnung
    ADD CONSTRAINT fkrvbnxfacldipkbyrkt9w8ylex FOREIGN KEY (email_id) REFERENCES public.email(id);


--
-- Name: lieferant_reklamation fks18k0x598vcgjadie04an4ykh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lieferant_reklamation
    ADD CONSTRAINT fks18k0x598vcgjadie04an4ykh FOREIGN KEY (erstellt_von_id) REFERENCES public.mitarbeiter(id);


--
-- Name: projekt_dokument fks3kbr4k2j7s0rmjlav1660el8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_dokument
    ADD CONSTRAINT fks3kbr4k2j7s0rmjlav1660el8 FOREIGN KEY (projekt) REFERENCES public.projekt(id);


--
-- Name: beleg_kostenstellen_anteil fks5u7c22mg6thmf7vk1yb0p4eb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.beleg_kostenstellen_anteil
    ADD CONSTRAINT fks5u7c22mg6thmf7vk1yb0p4eb FOREIGN KEY (zugeordnet_von_user_id) REFERENCES public.frontend_user_profile(id);


--
-- Name: miete_kostenstelle fks6hr5d8ibqwc878l2v7buysip; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.miete_kostenstelle
    ADD CONSTRAINT fks6hr5d8ibqwc878l2v7buysip FOREIGN KEY (mietobjekt_id) REFERENCES public.mietobjekt(id);


--
-- Name: zeitbuchung_audit fks9oi4hhr8g5di5jp6sw8srtb7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung_audit
    ADD CONSTRAINT fks9oi4hhr8g5di5jp6sw8srtb7 FOREIGN KEY (geaendert_von_mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: zeitbuchung fkt0fhw59yfj8sx7sy8jcacs13y; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zeitbuchung
    ADD CONSTRAINT fkt0fhw59yfj8sx7sy8jcacs13y FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- Name: abwesenheit fkt0ubrnf7d1gpsymykqurc95ih; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abwesenheit
    ADD CONSTRAINT fkt0ubrnf7d1gpsymykqurc95ih FOREIGN KEY (langzeitkrankmeldung_phase_id) REFERENCES public.langzeitkrankmeldung_phase(id);


--
-- Name: projekt_notiz fktco2khbngbo2vh7pmesx8cqbv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projekt_notiz
    ADD CONSTRAINT fktco2khbngbo2vh7pmesx8cqbv FOREIGN KEY (mitarbeiter_id) REFERENCES public.mitarbeiter(id);


--
-- PostgreSQL database dump complete
--


