package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequence")
    @SequenceGenerator(name = "sequence", sequenceName = "sequence", allocationSize = 100)
    @Column(columnDefinition = "INT", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private ZonedDateTime statusTime;

    @Column(columnDefinition = "TEXT", name = "last_error")
    private String lastError;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "site", fetch = FetchType.EAGER)
    private List<Page> page;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private List<Lemma> lemma;

    public Site(Status status, ZonedDateTime statusTime, String url, String name) {
        this.status = status;
        this.statusTime = statusTime;
        this.url = url;
        this.name = name;
    }
}
