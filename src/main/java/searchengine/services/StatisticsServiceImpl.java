package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Input;
import searchengine.config.InputList;
import searchengine.dto.statistics.*;
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
            suffix = suffix.substring(0, suffix.indexOf("/") + 1);
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

                result.putAll(lemmatizer.lemmatizeText(page.getContent()));

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
    public synchronized StatisticsResponse search(String query, String siteString, Long offset, Long limit) {

        if (query.trim().isEmpty()) {
            return new StatisticsResponse(false, "Задан пустой поисковый запрос");
        }
        List<Site> siteList = new ArrayList<>();

        if (siteString == null) {

            if (!siteRepository.findAllByStatus(Status.INDEXING).isEmpty()) {
                return new StatisticsResponse(false, "Идет индексация сайтов");
            } else if (!siteRepository.findAllByStatus(Status.FAILED).isEmpty()) {
                return new StatisticsResponse(false, "Ошибка индексации сайтов, необходимо перезапустить индексацию");
            }
            siteList.addAll(siteRepository.findAllByStatus(Status.INDEXED));
        } else {

            Site siteCurrent = siteRepository.findByUrl(siteString);
            if (siteCurrent == null) {
                return new StatisticsResponse(false, "Указанная страница не найдена");
            } else if (siteCurrent.getStatus().equals(Status.INDEXING)) {
                return new StatisticsResponse(false, "Идет индексация сайта");
            }
            siteList.add(siteCurrent);
        }

        Long count = 0L;
        List<Page> relevanceList = new ArrayList<>();
        HashMap<Page, Float> relevanceMap = new HashMap<>();

        for
        (Site site : siteList) {

            log.info("StatisticsServiceImpl in search started searching for query: {} and site: {}", query, site.getUrl());

            HashMap<String, Long> sortedResult = lemmatizer.lemmatizeText(query)
                    .entrySet()
                    .stream()
                    .peek(e -> {
                        Lemma lemma = lemmaRepository.findByLemmaAndSite(e.getKey(), site);
                        e.setValue(lemma == null ? 0L : lemma.getFrequency());
                    })
                    .sorted(Map.Entry.comparingByValue())
                    .limit(20)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e1, LinkedHashMap::new));

            if (!sortedResult.containsValue(0L)) {

                log.info("StatisticsServiceImpl in search has query: {} and sortedResultMap: {}", query, sortedResult);

                List<String> stringsLemmas = sortedResult.keySet().stream().toList();
                log.info("StatisticsServiceImpl in search has stringsLemmas {}", stringsLemmas);

                Lemma lemma = lemmaRepository.findByLemmaAndSite(stringsLemmas.get(0), site);
                log.info("StatisticsServiceImpl in search find lemma: {}", lemma.getLemma());

                List<Index> indexList = indexRepository.findAllByLemma(lemma);
                log.info("StatisticsServiceImpl in search find indexList: {}", indexList.toArray());

                List<Page> pageList = new ArrayList<>();
                indexList.forEach(i -> pageList.add(i.getPage()));

                log.info("StatisticsServiceImpl in search find Pages: {}", pageList);
                List<Page> reducedPageList = pageList;
                for (String word : sortedResult.keySet()) {

                    reducedPageList = reducedPageList.stream()
                            .filter(p -> !indexRepository.findAllByLemmaAndPage(lemmaRepository.findByLemmaAndSite(word, site), p).isEmpty())
                            .toList();
                }

                log.info("StatisticsServiceImpl in search result (reduced): {}", reducedPageList);

                count += reducedPageList.size();
                relevanceList.addAll(reducedPageList);
            }
        }

        Float totalAbsoluteRelevance = 0F;
        HashMap<String, Long> sortedResult = lemmatizer.lemmatizeText(query);
        for (Page page : relevanceList) {
            Float absoluteRelevance = 0F;
            for (String word : sortedResult.keySet()) {
                absoluteRelevance += indexRepository.findByLemmaAndPage(lemmaRepository.findByLemmaAndSite(word, page.getSite()), page).getRank();
            }
            relevanceMap.put(page, absoluteRelevance);
            totalAbsoluteRelevance = (totalAbsoluteRelevance > absoluteRelevance) ? totalAbsoluteRelevance : absoluteRelevance;
            log.info("StatisticsServiceImpl in search get page in relevance list: {}", page.getId());
            log.info("StatisticsServiceImpl in search get absolute relevance: {}", absoluteRelevance);
            log.info("StatisticsServiceImpl in search get total absolute relevance: {}", totalAbsoluteRelevance);
        }

        Float finalTotalAbsoluteRelevance = totalAbsoluteRelevance;

        relevanceMap = relevanceMap.entrySet()
                .stream()
                .peek(e -> e.setValue(e.getValue() / finalTotalAbsoluteRelevance))
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit + offset)
                .skip(offset)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        log.info("StatisticsServiceImpl in search get relevanceMap: {}", relevanceMap);


        List<SearchData> data = new ArrayList<>();
        relevanceMap.forEach((p, value) -> data.add(new SearchData(p.getSite().getUrl(), p.getSite().getName(), p.getPath(),
                Jsoup.parse(p.getContent()).title(), getSnippet(p, query), value)));

        log.info("StatisticsServiceImpl in getSnippet FINALLY GOT searchDataList {}", data);

        return new StatisticsResponse(true, count, data);
    }

    private synchronized String getSnippet(Page page, String query) {

        log.info("StatisticsServiceImpl in getSnippet started for Page: {}, and query: {}", page.getPath(), query);

        List<String> queryWords = lemmatizer.lemmatizeText(query).keySet().stream().toList();

        try {
            LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
            List<String> queryBaseWords = queryWords.stream().peek(q -> luceneMorphology.getNormalForms(q).get(0)).toList();

//            String pageText = Jsoup.parse(page.getContent()).text().replaceAll("\\s+", " ");
            String pageText = Jsoup.parse(page.getContent()).text().strip();
            log.info("StatisticsServiceImpl get pageText: {}", pageText);
            List<String> sentences = List.of(pageText.split("\\."));
            sentences = sentences
                    .stream()
                    .map(s -> s.concat("."))
                    .toList();

            List<String> shortSentences = new ArrayList<>();

            for (String s : sentences) {

                while (s.length() > 200) {
                    int count = s.indexOf(" ", 100);
                    if (count > 200) {
                        s = "";
                        continue;
                    }
                    shortSentences.add(s.substring(0, count));
                    s = s.substring(count);
                }

                shortSentences.add(s);
            }

            HashMap<String, Long> resultSnippetMap = new HashMap<>();

            log.info("StatisticsServiceImpl get sentences: {}", shortSentences);
            StringBuilder temp = new StringBuilder();
            Long counter = (long) shortSentences.size();
            for (String s : shortSentences) {

                counter--;
                if (temp.length() > 300 || counter == 0) {
                    List<String> pageString = List.of(temp.toString().replaceAll("[^А-я]", " ").trim().split("\s+"));
                    log.info("StatisticsServiceImpl get pageString: {}", pageString);
                    log.info("StatisticsServiceImpl get pageString size: {}", pageString.size());
                    if (pageString.size() > 1) {
                        HashSet<String> initialWords = new HashSet<>();
                        HashSet<String> baseWords = new HashSet<>();
                        pageString.forEach(string -> {
                            if (luceneMorphology.checkString(string.toLowerCase())) {
                                String normalFormString = luceneMorphology.getNormalForms(string.toLowerCase()).get(0);
                                if (queryBaseWords.contains(normalFormString)) {
                                    initialWords.add(string);
                                    baseWords.add(normalFormString);

                                }
                            }
                        });

                        String snippet = temp.toString();

                        for (String w : initialWords) {
                            log.info("StatisticsServiceImpl get w: {}", w);
                            snippet = snippet.replaceAll(w, "<b>" + w + "</b>");

                        }
                        log.info("StatisticsServiceImpl get snippet: {}", snippet);
                        resultSnippetMap.put(snippet, (long) baseWords.size());
                    }
                    temp = new StringBuilder(s);
                } else {
                    temp.append(" ").append(s);
                }

            }

            resultSnippetMap = resultSnippetMap
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(1)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e1, LinkedHashMap::new));


            log.info("StatisticsServiceImpl get resultSnippetMap: {}", resultSnippetMap);


            return resultSnippetMap.keySet().stream().findFirst().orElse(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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


        HashMap<String, Long> result = new HashMap<>(lemmatizer.lemmatizeText(page.getContent()));


        log.info("StatisticsServiceImpl in indexPage indexed page: {}, result: {}. " +
                "Going to make some indexes and lemmas.", page.getPath(), result);
        synchronized (lemmaRepository) {
            result.forEach((key, value) -> {
                Lemma currentLemma = lemmaRepository.findByLemmaAndSite(key, page.getSite());
                if (siteRepository.findAllByStatus(Status.FAILED).isEmpty()) {

                    if (currentLemma == null) {

                        indexRepository.save(new Index(page, lemmaRepository.save(new Lemma(page.getSite(), key, 1L)), value.floatValue()));
                    } else {

                        currentLemma.setFrequency(currentLemma.getFrequency() + 1L);
                        indexRepository.save(new Index(page, lemmaRepository.save(currentLemma), value.floatValue()));
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
    }

    private synchronized void dropDatabase() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

}
