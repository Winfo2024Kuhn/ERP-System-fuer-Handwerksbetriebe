package org.example.kalkulationsprogramm.config;

import java.sql.Types;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.LongVarbinaryJdbcType;
import org.hibernate.type.descriptor.jdbc.LongVarcharJdbcType;

/**
 * Lange Texte und Binärdaten ({@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)} bzw.
 * {@code LONG32VARBINARY}) lassen sich auf PostgreSQL auch leer ({@code null}) speichern.
 *
 * <p>Hibernate bindet {@code null} mit {@code setNull(index, typcode)} und reicht dabei seinen
 * eigenen Code durch (4001/4003, kein {@code java.sql.Types}-Wert). Der MySQL-Treiber ignoriert
 * den Typ, der PostgreSQL-Treiber lehnt ihn ab ("Unknown Types value", SQLState 07006) - z. B.
 * beim Mail-Import einer Nachricht ohne HTML-Teil.</p>
 *
 * <p>Hier binden beide Typen auf PostgreSQL mit dem Standard-JDBC-Code. Registriert bleiben sie
 * unter dem Hibernate-Code: Spaltentyp ({@code text}/{@code bytea}), Schema-Prüfung und Lesen
 * ändern sich nicht. Eingebunden über
 * {@code META-INF/services/org.hibernate.boot.model.TypeContributor}; Hibernate ruft das nach dem
 * Dialekt auf, der Eintrag hier ersetzt dessen.</p>
 */
public class PostgresLangtextTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        if (!(serviceRegistry.requireService(JdbcServices.class).getDialect() instanceof PostgreSQLDialect)) {
            return;
        }
        typeContributions.contributeJdbcType(new LongVarcharJdbcType(SqlTypes.LONG32VARCHAR) {
            @Override
            public int getJdbcTypeCode() {
                return Types.LONGVARCHAR;
            }

            @Override
            public int getDefaultSqlTypeCode() {
                return SqlTypes.LONG32VARCHAR;
            }
        });
        typeContributions.contributeJdbcType(new LongVarbinaryJdbcType(SqlTypes.LONG32VARBINARY) {
            @Override
            public int getJdbcTypeCode() {
                return Types.LONGVARBINARY;
            }

            @Override
            public int getDefaultSqlTypeCode() {
                return SqlTypes.LONG32VARBINARY;
            }
        });
    }
}
