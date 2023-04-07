package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "lemma")
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequence")
    @SequenceGenerator(name = "sequence", sequenceName = "sequence", allocationSize = 100)
    @Column(columnDefinition = "INT", nullable = false)
    private Long id;

    @ManyToOne
    private Site site;

    @Column(nullable = false)
    private String lemma;

    @Column(columnDefinition = "INT", nullable = false)
    private Long frequency;

    @OneToMany(mappedBy = "lemma")
    private List<Index> index;

    public Lemma(Site site, String lemma, Long frequency) {
        this.site = site;
        this.lemma = lemma;
        this.frequency = frequency;
    }
}
