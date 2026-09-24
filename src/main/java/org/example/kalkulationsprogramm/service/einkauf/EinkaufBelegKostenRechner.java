package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;

/** Compares billed shares; fixed surcharges have one allowance, not one per invoice. */
final class EinkaufBelegKostenRechner {
    private static final Set<String> ZUSAETZE=Set.of("FRACHT","BEARBEITUNG","VERPACKUNG","ZEUGNIS","ZUSCHNITT","MINDERMENGE","RABATT");
    record Ergebnis(BigDecimal vereinbart,BigDecimal abgerechnet,boolean vollstaendig) {}
    private EinkaufBelegKostenRechner() {}

    static Ergebnis berechne(List<EinkaufBelegPosition> bills,Map<Long,EinkaufBelegZuordnung> types,
            List<Kosten> headerCosts,Set<String> usedHeaderAllowances) {
        BigDecimal agreed=BigDecimal.ZERO,actual=BigDecimal.ZERO;
        boolean complete=true;
        Map<String,BigDecimal> flatActual=new LinkedHashMap<>(),flatAllowed=new HashMap<>();
        Set<String> headerKeys=new HashSet<>();
        for(var bill:bills) {
            var line=bill.getBestellPosition();
            if(bill.isPruefen()||bill.getEinzelpreis()==null||line==null||line.getNettoEinzelpreis()==null) {complete=false;continue;}
            var type=types.get(bill.getDokument().getId());
            BigDecimal sign=EinkaufRechnungsabgleichService.signed(type,BigDecimal.ONE);
            actual=actual.add(bill.getEinzelpreis().multiply(bill.getMenge()).multiply(sign));
            if(!bill.isNurPreisKorrektur())agreed=agreed.add(line.getNettoEinzelpreis().multiply(bill.getMenge()).multiply(sign));
            var basis=basis(line,bill.getMenge(),bill.getEinheit());
            var invoiced=bill.getKosten().stream().map(EinkaufBelegKostenRechner::kosten).toList();
            var invoiceCalc=EinkaufVergleichService.berechneKosten(withMaterial(invoiced,bill.getEinzelpreis(),bill.getMenge()),basis);
            if(!invoiceCalc.vollstaendig()) {complete=false;continue;}
            // The synthetic material component is contained, so only actual extras are added.
            actual=actual.add(invoiceCalc.nettoGesamt().multiply(sign));
            Map<String,BigDecimal> invoiceValues=new HashMap<>();
            invoiceCalc.rechnung().forEach(r->invoiceValues.put(r.key(),r.ergebnis()));
            List<Kosten> orderCosts=new ArrayList<>();
            line.getKosten().stream().filter(c->c.get("schluessel")!=null).map(EinkaufBelegKostenRechner::kosten).forEach(orderCosts::add);
            Set<String> positionKeys=new HashSet<>();orderCosts.forEach(c->positionKeys.add(c.schluessel()));
            headerCosts.stream().filter(c->!positionKeys.contains(c.schluessel())).forEach(orderCosts::add);
            var orderCalc=EinkaufVergleichService.berechneKosten(withMaterial(orderCosts,line.getNettoEinzelpreis(),bill.getMenge()),basis);
            Map<String,BigDecimal> orderValues=new HashMap<>();orderCalc.rechnung().forEach(r->orderValues.put(r.key(),r.ergebnis()));
            for(var cost:invoiced) {
                if(cost.enthalten())continue;
                var counterpart=orderCosts.stream().filter(c->Objects.equals(c.schluessel(),cost.schluessel())).findFirst().orElse(null);
                if(counterpart==null)continue; // Explicit, non-agreed surcharge: allowance is zero.
                if(!Objects.equals(counterpart.art(),cost.art())){complete=false;continue;}
                if(counterpart.enthalten()||!ZUSAETZE.contains(counterpart.art()))continue;
                var allowed=orderValues.get(counterpart.schluessel());
                if(allowed==null){complete=false;continue;}
                BigDecimal costSign="RABATT".equals(cost.art())?BigDecimal.ONE.negate():BigDecimal.ONE;
                if("PAUSCHAL".equals(counterpart.basis())) {
                    boolean header=!positionKeys.contains(counterpart.schluessel());
                    String scope=header?"HEADER:"+("FRACHT".equals(cost.art())?new TreeMap<>(line.getLiefergruppe()).toString():"BESTELLUNG")
                        :"POSITION:"+lineIdentity(line);
                    String key=scope+":"+counterpart.schluessel();
                    flatActual.merge(key,invoiceValues.get(cost.schluessel()).multiply(sign).multiply(costSign),BigDecimal::add);
                    flatAllowed.putIfAbsent(key,allowed.multiply(costSign));
                    if(header)headerKeys.add(key);
                } else if(!bill.isNurPreisKorrektur())agreed=agreed.add(allowed.multiply(sign).multiply(costSign));
            }
        }
        for(var item:flatActual.entrySet())if(item.getValue().signum()!=0
                &&(!headerKeys.contains(item.getKey())||usedHeaderAllowances.add(item.getKey())))agreed=agreed.add(flatAllowed.get(item.getKey()));
        return new Ergebnis(complete ? agreed.setScale(2,RoundingMode.HALF_UP) : null,
                complete ? actual.setScale(2,RoundingMode.HALF_UP) : null, complete);
    }
    static String lineIdentity(BestellungPosition line) {
        var origins=line.getHerkuenfte().stream().map(BestellungHerkunft::getBedarfId).distinct().sorted().toList();
        return origins.isEmpty()?"ID:"+line.getId():"BEDARF:"+origins;
    }
    static Mengenbasis basis(BestellungPosition line,BigDecimal quantity,Einheit unit) {
        var old=line.getPosition().basis();
        return old.mitAnteiligenGesamtwerten(quantity,unit,unit==Einheit.STUECK?quantity:old.stueckzahl());
    }
    static Kosten kosten(Map<String,Object> map) {
        return new Kosten(string(map.get("schluessel")),string(map.get("art")),decimal(map.get("betrag")),string(map.get("basis")),
            decimal(map.get("basisMenge")),string(map.get("prozentBasisSchluessel")),Boolean.TRUE.equals(map.get("enthalten")),
            Boolean.TRUE.equals(map.get("variabel")),string(map.get("quelle")));
    }
    private static List<Kosten> withMaterial(List<Kosten> costs,BigDecimal unitPrice,BigDecimal quantity) {
        var result=new ArrayList<>(costs);
        if(costs.stream().noneMatch(c->"material".equals(c.schluessel())))
            result.add(new Kosten("material","MATERIAL",unitPrice.multiply(quantity),"PAUSCHAL",BigDecimal.ONE,null,true,false,"Bestell-/Belegposition"));
        return result;
    }
    private static String string(Object value){return value==null?null:value.toString();}
    private static BigDecimal decimal(Object value){return value==null?null:new BigDecimal(value.toString());}
}
