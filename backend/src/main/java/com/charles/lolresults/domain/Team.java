package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court utilise dans les grilles de resultats, ex: "G2", "T1", "LGD". */
    @Column(nullable = false, length = 20)
    private String code;

    /** Nom complet affichable, ex: "G2 Esports". Modifiable librement via l'appli. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Region d'origine de l'equipe (facultatif), ex: "EMEA", "KR", "CN". */
    @Column(length = 50)
    private String region;

    /** Logo (image binaire, colonne bytea), servi via GET /api/teams/{id}/logo. Nul si pas encore recupere. */
    @Column(name = "logo")
    private byte[] logo;

    /** Type MIME du logo, ex: "image/png". */
    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    public Team(String code, String name, String region) {
        this.code = code;
        this.name = name;
        this.region = region;
    }
}
