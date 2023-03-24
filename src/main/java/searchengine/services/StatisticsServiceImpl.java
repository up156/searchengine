package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Input;
import searchengine.config.InputList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final InputList input;
    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = {"INDEXED", "FAILED", "INDEXING"};
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(input.getInput().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Input> sitesList = input.getInput();
        for (int i = 0; i < sitesList.size(); i++) {
            Input site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = random.nextInt(1_000);
            int lemmas = pages * random.nextInt(1_000);
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(statuses[i % 3]);
            item.setError(errors[i % 3]);
            item.setStatusTime(System.currentTimeMillis() -
                    (random.nextInt(10_000)));
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    @Override
    public StatisticsResponse startIndexing() {

        List<String> inputList = input.getInput().stream().map(Input::getUrl).toList();

        log.info("StatisticsServiceImpl in startIndexing started..");

        List<Site> list = inputList
                .stream()
                .map(siteRepository::findByUrl)
                .filter(Objects::nonNull)
                .toList();

        List<Site> indexingList = list.stream().filter(s -> s.getStatus().equals(Status.INDEXING)).toList();

        if (!indexingList.isEmpty()) {
            log.info("StatisticsServiceImpl in startIndexing: indexing already in progress");
            return new StatisticsResponse(false, "Индексация уже запущена");
        }

        if (!list.isEmpty()) {
            log.info("StatisticsServiceImpl in startIndexing deleted Sites: {} and Pages for this sites. Will be re-indexed", list);
            list.forEach(s -> pageRepository.deleteAll(pageRepository.findAllBySite(s)));
            siteRepository.deleteAll(list);
        }

        return indexSites(createSites());
    }

    private List<Site> createSites() {
        input.getInput().forEach(s -> siteRepository.save(new Site(Status.INDEXING,
                ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC),
                s.getUrl(), s.getName())));
        List<Site> result = input.getInput()
                .stream()
                .map(Input::getUrl)
                .map(siteRepository::findByUrl).toList();

        log.info("StatisticsServiceImpl in createSites creates sites: {}.", result);

        return result;

    }

    private StatisticsResponse indexSites(List<Site> siteList) {

        log.info("StatisticsServiceImpl in startIndexing started indexing sites: " + siteList);

        siteList.forEach(s -> {

                    Thread thread = new IndexThread(s, pageRepository, siteRepository);
                    thread.start();

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
        return null;
    }

    @Override
    public StatisticsResponse search(String query, String site, Long offset, Long limit) {
        return null;
    }
}
