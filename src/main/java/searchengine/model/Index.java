package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "\"index\"")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequence")
    @SequenceGenerator(name = "sequence", sequenceName = "sequence", allocationSize = 100)
    @Column(columnDefinition = "INT", nullable = false)
    private Long id;

    @ManyToOne
    private Page page;

    @ManyToOne
    private Lemma lemma;

    @Column(name = "\"rank\"",  nullable = false)
    private Float rank;



}
