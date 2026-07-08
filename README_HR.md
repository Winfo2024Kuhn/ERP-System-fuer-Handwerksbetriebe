# Handwerkerprogramm ERP

`Handwerkerprogramm` je open-source ERP sustav razvijen za obrtnička i građevinska poduzeća. Cilj projekta je spojiti kalkulaciju ponuda, upravljanje projektima, evidenciju radnog vremena, dokumente, nabavu, skladište, e-poštu i naknadnu kalkulaciju u jednu aplikaciju.

Projekt je izvorno razvijen za stvarne potrebe obrtničke firme, pa naglasak nije na generičkom ERP-u, nego na svakodnevnim procesima: od upita kupca, preko ponude i izvedbe, do računa, kontrole troškova i analize profitabilnosti.

## Glavne mogućnosti

- Upravljanje kupcima, dobavljačima, projektima i upitima
- Izrada ponuda, računa, djelomičnih računa, završnih računa, storna i odobrenja
- Blokovski editor dokumenata s PDF pregledom
- ZUGFeRD/XRechnung podrška za elektroničke račune
- Evidencija radnog vremena za radnike i projekte
- Mobilna PWA aplikacija za gradilište
- Projektni dnevnik s bilješkama i slikama
- Naknadna kalkulacija projekta u stvarnom vremenu
- Materijalni troškovi iz ručnog unosa, artikala, skladišta i ulaznih računa
- Upravljanje skladištem i zalihama
- Nabava i praćenje narudžbi
- Uvoz i obrada e-pošte s prilozima
- Analiza ulaznih računa uz pomoć AI/OCR obrade
- AI asistent koji koristi kontekst aplikacije i koda
- Dashboardi za controlling, promet i troškove
- Upravljanje odsutnostima, godišnjim odmorima i kalendarom tima

## Tipičan tijek rada

1. Kupac šalje upit putem web obrasca ili e-pošte.
2. Upit se obrađuje u sustavu, dodaju se bilješke, slike i dokumenti.
3. Iz upita se kreira projekt.
4. U dokument editoru izrađuje se ponuda.
5. Nakon prihvaćanja ponude projekt se izvodi na terenu.
6. Radnici preko mobilne aplikacije evidentiraju vrijeme, slike i bilješke.
7. Materijal, ulazni računi i skladišne promjene povezuju se s projektom.
8. Sustav prikazuje stvarne troškove, dobit i odstupanja od kalkulacije.
9. Iz istog sustava se izrađuje i šalje račun.

## Tehnologije

Backend:

- Kotlin
- Spring Boot 3
- Spring Security
- Spring Data JPA / Hibernate
- MariaDB za produkciju
- H2 profil za lokalni razvoj i brzu provjeru
- Flyway migracije
- Maven

Frontend:

- React
- TypeScript
- Vite
- Tailwind CSS
- PWA za mobilnu evidenciju vremena

Integracije i dodatne funkcije:

- IMAP e-mail import
- PDF generiranje i pregled
- ZUGFeRD/XRechnung
- AI/OCR obrada dokumenata
- Lokalno otvaranje CAD/Excel datoteka preko posebnog launcher protokola

## Struktura projekta

```text
src/main/kotlin/        Backend aplikacija u Kotlinu
src/main/resources/     Konfiguracija, migracije i statički build
react-pc-frontend/      Desktop React frontend
react-zeiterfassung/    Mobilna PWA aplikacija
deployment/             Skripte i upute za deployment
assets/                 Slike i prikazi za dokumentaciju
uploads/                Lokalni upload direktorij za razvoj
```

## Lokalno pokretanje

Za brzu lokalnu provjeru može se koristiti H2 profil:

```bash
MAVEN_OPTS="-Xms64m -Xmx512m" mvn -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=h2
```

Aplikacija se zatim otvara na:

```text
http://localhost:8080
```

Za produkciju se koristi MariaDB i odgovarajuće konfiguracijske vrijednosti u `application` profilima.

## Build

Backend build bez testova:

```bash
mvn -Dmaven.test.skip=true package
```

Frontend build se radi iz odgovarajućih React direktorija, ovisno o tome gradi li se desktop sučelje ili mobilna PWA.

## Deployment

Projekt se može pokretati kao Spring Boot aplikacija iza web servera/proxyja. Statički frontend se servira kroz Spring Boot iz `src/main/resources/static`.

Za hosting s ograničenom memorijom preporučena JVM postavka je:

```bash
-Xms64m -Xmx512m
```

Kod shared hostinga treba paziti na:

- dostupnu memoriju
- broj procesa
- trajno pokretanje Java procesa
- bazu podataka
- upload direktorije
- logove i cache direktorije

## Status konverzije na Kotlin

Glavni backend kod je prebačen na Kotlin. U zadnjim izmjenama dodatno su popravljeni važni dijelovi nakon konverzije:

- projektni endpointi
- osnovni projektni CRUD
- generiranje i provjera broja naloga
- čitanje dokumenata projekta
- čitanje bilješki projekta
- osnovni endpointi za ulazne račune projekta
- čitanje podataka za beleg/računovodstvene prikaze
- dio API-ja za evidenciju vremena
- dio servisa za dokumente
- lokalni H2 startup i ograničenje memorije

Testovi nisu dio ove provjere jer su u ovoj fazi namjerno preskočeni.

## Licenca

Projekt je objavljen kao open-source projekt pod licencom navedenom u datoteci `LICENSE`.

## Napomena

Ovaj sustav pokriva velik broj poslovnih procesa. Prije produkcijskog korištenja preporučuje se provjeriti konfiguraciju baze, korisnička prava, mail postavke, backup, upload direktorije i resurse hostinga.
