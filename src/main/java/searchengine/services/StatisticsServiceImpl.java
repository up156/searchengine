package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Input;
import searchengine.config.InputList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final InputList input;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;

    private final Lemmatizer lemmatizer;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    @Override
    public StatisticsResponse getStatistics() {

        log.info("StatisticsServiceImpl in getStatistics going to send statistics for the sites {}", input.getInput());

        if (input.getInput().isEmpty()) {
            StatisticsResponse response = new StatisticsResponse(true);
            response.setStatistics(new StatisticsData(new TotalStatistics(0L, 0L, 0L, false),
                    Collections.emptyList()));
            return response;
        }

        List<String> listUrls = input.getInput().stream().map(Input::getUrl).toList();
        List<Site> siteList = listUrls.stream().map(siteRepository::findByUrl).toList();

        if (siteList.get(0) == null) {
            StatisticsResponse response = new StatisticsResponse(true);
            response.setStatistics(new StatisticsData(new TotalStatistics(0L, 0L, 0L, false),
                    Collections.emptyList()));
            return response;
        }

        List<Page> listPage = new ArrayList<>();
        siteList.forEach(s -> listPage.addAll(pageRepository.findAllBySite(s)));
        List<Lemma> lemmaList = new ArrayList<>();
        siteList.forEach(s -> lemmaList.addAll(lemmaRepository.findAllBySite(s)));

        TotalStatistics total = new TotalStatistics((long) siteList.size(),
                (long) listPage.size(),
                (long) lemmaList.size(),
                !siteRepository.findAllByStatus(Status.INDEXING).isEmpty());

        List<DetailedStatisticsItem> detailedList = new ArrayList<>();

        siteList.forEach(s -> {
            DetailedStatisticsItem item = new DetailedStatisticsItem(s.getUrl(), s.getName(), s.getStatus(), s.getStatusTime(),
                    (long) pageRepository.findAllBySite(s).size(), (long) lemmaRepository.findAllBySite(s).size());
            if (s.getStatus().equals(Status.FAILED)) {
                item.setError(s.getLastError());
            }
            detailedList.add(item);
        });

        return new StatisticsResponse(true, new StatisticsData(total, detailedList));
    }

    @Override
    public StatisticsResponse startIndexing() {

        log.info("StatisticsServiceImpl in startIndexing started..");

        if (!siteRepository.findAllByStatus(Status.INDEXING).isEmpty()) {

            log.info("StatisticsServiceImpl in startIndexing: indexing already in progress");
            return new StatisticsResponse(false, "Индексация уже запущена");
        }

        if (!siteRepository.findAllByStatus(Status.FAILED).isEmpty() || !siteRepository.findAllByStatus(Status.INDEXED).isEmpty()) {

            log.info("StatisticsServiceImpl in startIndexing deleted Sites and Pages for this sites. Will be re-indexed");
            dropDatabase();

//                list.stream().map(pageRepository::findAllBySite).forEach(l -> l.forEach(this::deletePageLemmasIndexes));
//                list.forEach(siteRepository::delete);
        }

        List<Site> list = input.getInput().stream().map(i -> siteRepository.findByUrl(i.getUrl())).filter(Objects::nonNull).toList();

        return indexSites(createSites());
    }

    private List<Site> createSites() {

        List<Site> result = new ArrayList<>();
        for (Input initialInput : input.getInput()) {
            Site newSite = new Site(Status.INDEXING,
                    ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC),
                    initialInput.getUrl(), initialInput.getName());
            siteRepository.save(newSite);
            result.add(newSite);
        }

        log.info("StatisticsServiceImpl in createSites creates sites: {}.", result);

        return result;

    }

    private StatisticsResponse indexSites(List<Site> siteList) {

        log.info("StatisticsServiceImpl in startIndexing started indexing sites: " + siteList);

        siteList.forEach(s -> {

                    ExecutorService executors = Executors.newSingleThreadExecutor();

                    executors.execute(new IndexThread(s, this, pageRepository, siteRepository));
                    executors.shutdown();

                }
        );


        return new StatisticsResponse(true);
    }

    @Override
    public StatisticsResponse stopIndexing() {
        List<Site> siteList = siteRepository.findAllByStatus(Status.INDEXING);
        if (!siteList.isEmpty()) {
            siteList.forEach(s -> {
                s.setStatus(Status.FAILED);
                s.setLastError("Прервано пользователем");
                s.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
                siteRepository.save(s);
            });
            return new StatisticsResponse(true);
        } else
            return new StatisticsResponse(false, "Индексация не запущена");
    }

    @Override
    public StatisticsResponse indexPage(String url) {

        log.info("StatisticsServiceImpl in indexPage started process for the page: " + url);

        HashMap<String, Long> result = new HashMap<>();
        String suffix = (url.substring(url.indexOf(".")));
        if (suffix.contains("/")) {
            suffix = suffix.substring(0, suffix.indexOf("/"));
        }

        Site site = siteRepository.findByUrl(url.substring(0, url.indexOf(suffix) + suffix.length()));

        String path = url.substring(url.indexOf(".") + suffix.length());

        List<Page> pageList = pageRepository.findAllByPathAndSite(path, site).stream().filter(Objects::nonNull).toList();
        if (site.getName().isEmpty()) {
            return new StatisticsResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");

        } else {

            if (!pageList.isEmpty()) {

                log.info("StatisticsServiceImpl in indexPage started deleting page, lemmas and indexes for {}: " + url);

                pageList.forEach(this::deletePageLemmasIndexes);

            }

            log.info("StatisticsServiceImpl in indexPage started indexing page: " + url);

            try {
                String text = Jsoup.connect(url).followRedirects(false).timeout(20000).get().body().wholeText();
                Page page = new Page(site, path, 200L, text);
                pageRepository.save(page);
                log.info("StatisticsServiceImpl in indexPage saved page: " + page.getPath());
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            pageList = pageRepository.findAllByPathAndSite(path, site).stream().filter(Objects::nonNull).toList();

            pageList.forEach(page -> {
                try {
                    result.putAll(lemmatizer.lemmatizeText(page.getContent()));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                HashMap<String, Long> sortedResult = result.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (e1, e2) -> e1, LinkedHashMap::new));

                log.info("StatisticsServiceImpl in indexPage indexed page: {}, result: {}. " +
                        "Going to make some indexes and lemmas." + url, sortedResult);

                sortedResult.forEach((key, value) -> {
                    List<Lemma> currentLemmas = lemmaRepository.findAllByLemma(key);
                    if (currentLemmas.isEmpty()) {
                        indexRepository.save(new Index(page, lemmaRepository.save(new Lemma(site, key, 1L)), value.floatValue()));
                    } else {
                        Lemma lemma = currentLemmas.get(0);
                        lemma.setFrequency(lemma.getFrequency() + 1L);
                        indexRepository.save(new Index(page, lemmaRepository.save(lemma), value.floatValue()));
                    }
                });

            });

            return new StatisticsResponse(true);
        }
    }

    @Override
    public StatisticsResponse search(String query, String siteString, Long offset, Long limit) {

        if (query.trim().isEmpty()) {
            return new StatisticsResponse(false, "Задан пустой поисковый запрос");
        }
        List<Site> siteList;
        if (siteString.trim().isEmpty() && siteRepository.findAllByStatus(Status.INDEXING).isEmpty()) {
            siteList = siteRepository.findAllByStatus(Status.INDEXED);
        } else {
            return new StatisticsResponse(false, "Идет индексация сайтов");
        }

        Site site = siteRepository.findByUrl(siteString);

        if (site == null || !site.getStatus().equals(Status.INDEXED)) {

            return new StatisticsResponse(false, "Указанная страница не найдена");
        } else {
            siteList.add(site);
        }

        try {
            HashMap<String, Long> sortedResult = lemmatizer.lemmatizeText(query)
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e1, LinkedHashMap::new));

//            List<Page> pageList = pageRepository.find indexRepository.findByLemma(
//                    lemmaRepository.findByLemma(sortedResult.keySet().stream().findFirst().orElse("")));
//            sortedResult.keySet().stream().map();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private synchronized void deletePageLemmasIndexes(Page page) {

        log.info("StatisticsServiceImpl in deletePageLemmasIndexes started deleting page, lemmas and indexes for {}: ", page);

        List<Index> indexList = indexRepository.findAllByPageId(page.getId());
        List<Lemma> lemmaList = indexList.stream().map(Index::getLemma).toList();
        lemmaList.forEach(l -> {
            Long frequency = l.getFrequency();
            if (frequency == 1L) {
                lemmaRepository.delete(l);
            } else {
                l.setFrequency(frequency - 1L);
                lemmaRepository.save(l);
            }
        });

        indexRepository.deleteAll(indexList);
        pageRepository.delete(page);
    }

    public void indexNewPage(Page page) {

        log.info("StatisticsServiceImpl in indexPage started indexing new page: {}", page.getPath());


        try {
            HashMap<String, Long> result = new HashMap<>(lemmatizer.lemmatizeText(page.getContent()));


            log.info("StatisticsServiceImpl in indexPage indexed page: {}, result: {}. " +
                    "Going to make some indexes and lemmas.", page.getPath(), result);
            synchronized (lemmaRepository) {
                result.forEach((key, value) -> {
                    List<Lemma> currentLemmas = lemmaRepository.findAllByLemma(key);
                    if (siteRepository.findAllByStatus(Status.FAILED).isEmpty()) {

                        if (currentLemmas.isEmpty()) {

                            indexRepository.save(new Index(page, lemmaRepository.save(new Lemma(page.getSite(), key, 1L)), value.floatValue()));
                        } else {
                            Lemma lemma = currentLemmas.get(0);
                            lemma.setFrequency(lemma.getFrequency() + 1L);
                            indexRepository.save(new Index(page, lemmaRepository.save(lemma), value.floatValue()));
                        }

                    } else {
                        System.out.println("LEMMA BREAK");
                    }
                });
            }
            Site site = siteRepository.findById(page.getSite().getId()).get();

            if (!site.getStatus().equals(Status.FAILED)) {

                site.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
                siteRepository.save(site);

            }


        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    private synchronized void dropDatabase() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

}
