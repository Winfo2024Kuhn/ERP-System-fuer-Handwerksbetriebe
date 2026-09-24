package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ArtikelServiceTest {
    private ArtikelRepository artikelRepository;
    private EntityManager entityManager;
    private TypedQuery<Long> query;
    private ArtikelService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        artikelRepository = mock(ArtikelRepository.class);
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("nummer"), anyString())).thenReturn(query);
        service = new ArtikelService(artikelRepository, mock(KategorieRepository.class),
                mock(WerkstoffRepository.class), mock(LieferantenRepository.class), entityManager);
    }

    @Test
    void lieferantBleibtOhnePreisUndExterneNummerZugeordnet() {
        var suppliers=mock(LieferantenRepository.class);
        var supplier=new org.example.kalkulationsprogramm.domain.Lieferanten();
        supplier.setId(7L); supplier.setLieferantenname("Dummy Lieferant");
        when(suppliers.findById(7L)).thenReturn(java.util.Optional.of(supplier));
        var articleService=new ArtikelService(artikelRepository,mock(KategorieRepository.class),mock(WerkstoffRepository.class),suppliers,entityManager);
        when(query.getSingleResult()).thenReturn(0L);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(call->{Artikel a=call.getArgument(0);a.setId(57L);return a;});
        when(artikelRepository.saveAndFlush(any(Artikel.class))).thenAnswer(call->call.getArgument(0));
        var request=new ArtikelCreateDto();request.setProduktname("Dummy Profil");request.setLieferantId(7L);
        var saved=articleService.erstelleArtikel(request);
        assertEquals(1,saved.getArtikelpreis().size());
        var relation=saved.getArtikelpreis().iterator().next();
        assertSame(supplier,relation.getLieferant());
        assertNull(relation.getPreis());
        assertNull(relation.getExterneArtikelnummer());
        verify(artikelRepository,times(2)).save(saved);
    }

    @Test
    void createCallerOhneInterneNummerErhaeltKollisionssichereNummerAusId() {
        when(query.getSingleResult()).thenReturn(0L);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(invocation -> {
            Artikel artikel = invocation.getArgument(0);
            if (artikel.getId() == null) artikel.setId(55L);
            return artikel;
        });
        when(artikelRepository.saveAndFlush(any(Artikel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Artikel result = service.erstelleArtikel(new ArtikelCreateDto());

        assertEquals("ART-55", result.getArtikelnummer());
        verify(artikelRepository).save(result);
        verify(artikelRepository).saveAndFlush(result);
    }

    @Test
    void besetzteBasisnummerErhaeltReserviertenSuffix() {
        when(query.getSingleResult()).thenReturn(1L, 0L);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(invocation -> {
            Artikel artikel = invocation.getArgument(0);
            if (artikel.getId() == null) artikel.setId(55L);
            return artikel;
        });
        when(artikelRepository.saveAndFlush(any(Artikel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Artikel result = service.erstelleArtikel(new ArtikelCreateDto());

        assertEquals("ART-55-2", result.getArtikelnummer());
    }

    @Test
    void explizitDoppelteInterneNummerWirdAlsKonfliktAbgewiesen() {
        when(query.getSingleResult()).thenReturn(1L);
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setArtikelnummer("INT-55");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.erstelleArtikel(dto));

        assertEquals(409, error.getStatusCode().value());
        verify(artikelRepository, never()).save(any(Artikel.class));
    }

    @Test
    void expliziteEigeneNummerWirdAmArtikelGespeichert() {
        when(query.getSingleResult()).thenReturn(0L);
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(invocation -> {
            Artikel artikel = invocation.getArgument(0);
            if (artikel.getId() == null) artikel.setId(56L);
            return artikel;
        });
        when(artikelRepository.saveAndFlush(any(Artikel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setArtikelnummer("ST-RR-042");

        Artikel result = service.erstelleArtikel(dto);

        assertEquals("ST-RR-042", result.getArtikelnummer());
        verify(artikelRepository).saveAndFlush(result);
    }
}
