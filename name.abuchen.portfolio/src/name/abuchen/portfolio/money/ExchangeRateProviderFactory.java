package name.abuchen.portfolio.money;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.impl.ChainedExchangeRateTimeSeries;
import name.abuchen.portfolio.money.impl.InverseExchangeRateTimeSeries;

/**
 * A factory for {@link ExchangeRateProvider}s linked to a client.
 */
public class ExchangeRateProviderFactory
{
    private class Dijkstra
    {
        private List<ExchangeRateTimeSeries> timeSeries = new ArrayList<>();

        private Set<String> visited = new HashSet<>();
        private Set<String> unvisited = new HashSet<>();
        private Map<String, ExchangeRateTimeSeries> predecessors = new HashMap<>();
        private Map<String, Integer> distance = new HashMap<>();

        public Dijkstra(List<ExchangeRateTimeSeries> availableTimeSeries, String baseCurrency)
        {
            availableTimeSeries.stream().forEach(ts -> {
                timeSeries.add(ts);
                timeSeries.add(new InverseExchangeRateTimeSeries(ts));
            });

            distance.put(baseCurrency, 0);

            unvisited = CurrencyUnit.getAvailableCurrencyUnits().stream().map(CurrencyUnit::getCurrencyCode)
                            .collect(Collectors.toSet());

            if (!unvisited.contains(baseCurrency))
                return;

            while (!unvisited.isEmpty())
            {
                String node = getNodeWithMinimumDistance(unvisited);
                unvisited.remove(node);
                visited.add(node);

                for (ExchangeRateTimeSeries neighbor : getNeighbors(node))
                {
                    String neighborNode = neighbor.getTermCurrency();
                    int alternativeDistance = getDistance(node) + neighbor.getWeight();

                    if (alternativeDistance < getDistance(neighborNode))
                    {
                        distance.put(neighborNode, alternativeDistance);
                        predecessors.put(neighborNode, neighbor);
                    }
                }
            }
        }

        public List<ExchangeRateTimeSeries> findShortestPath(String termCurrency)
        {
            ExchangeRateTimeSeries current = predecessors.get(termCurrency);
            if (current == null)
                return Collections.emptyList();

            LinkedList<ExchangeRateTimeSeries> answer = new LinkedList<>();
            while (current != null)
            {
                answer.addFirst(current);
                current = predecessors.get(current.getBaseCurrency());
            }

            return answer;
        }

        private List<ExchangeRateTimeSeries> getNeighbors(String node)
        {
            return timeSeries.stream()
                            .filter(ts -> ts.getBaseCurrency().equals(node) && !visited.contains(ts.getTermCurrency()))
                            .collect(Collectors.toList());
        }

        private String getNodeWithMinimumDistance(Set<String> currencies)
        {
            return currencies.stream().min(Comparator.comparingInt(this::getDistance))
                            .orElse(currencies.iterator().next());
        }

        private int getDistance(String currency)
        {
            Integer d = distance.get(currency);
            return d == null ? Integer.MAX_VALUE : d;
        }
    }

    private static class CurrencyPair
    {
        private final String base;
        private final String term;

        public CurrencyPair(String base, String term)
        {
            this.base = base;
            this.term = term;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(base, term);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            CurrencyPair other = (CurrencyPair) obj;

            if (!Objects.equals(base, other.base))
                return false;

            return Objects.equals(term, other.term);
        }

    }

    private final Client client;
    private final List<ExchangeRateProvider> providers = new ArrayList<>();
    private final ConcurrentMap<CurrencyPair, ExchangeRateTimeSeries> cache = new ConcurrentHashMap<>();

    public ExchangeRateProviderFactory(Client client)
    {
        this.client = client;
        // load all available providers
        loadAllProviders(providers);
    }

    /**
     * Gets all available {@link ExchangeRateProvider}s.
     * 
     * @return {@link ExchangeRateProvider}s
     */
    public static List<ExchangeRateProvider> getProviders()
    {
        List<ExchangeRateProvider> l = new ArrayList<>();
        loadAllProviders(l);
        return l;
    }

    /**
     * Loads all {@link ExchangeRateProvider}s and adds them to the given
     * {@link Collection}.
     * 
     * @param col
     *            {@link Collection}
     */
    private static void loadAllProviders(Collection<ExchangeRateProvider> col)
    {
        // load all available providers
        Iterator<ExchangeRateProvider> registeredProvider = ServiceLoader.load(ExchangeRateProvider.class).iterator();
        while (registeredProvider.hasNext())
        {
            ExchangeRateProvider provider = registeredProvider.next();
            col.add(provider);
        }
    }

    /**
     * Returns the available exchange rates provided by this provider.
     * 
     * @return available time series
     */
    public List<ExchangeRateTimeSeries> getAvailableTimeSeries()
    {
        List<ExchangeRateTimeSeries> series = new ArrayList<>();
        for (ExchangeRateProvider p : providers)
        {
            series.addAll(p.getAvailableTimeSeries(client));
        }
        return series;
    }

    public void clearCache()
    {
        cache.clear();
    }

    public ExchangeRateTimeSeries getTimeSeries(String baseCurrency, String termCurrency)
    {
        return cache.computeIfAbsent(new CurrencyPair(baseCurrency, termCurrency),
                        pair -> computeTimeSeries(baseCurrency, termCurrency));
    }

    private ExchangeRateTimeSeries computeTimeSeries(String baseCurrency, String termCurrency)
    {
        Dijkstra dijkstra = new Dijkstra(getAvailableTimeSeries(), baseCurrency);
        List<ExchangeRateTimeSeries> answer = dijkstra.findShortestPath(termCurrency);

        if (answer.isEmpty())
            return null;
        else if (answer.size() == 1)
            return answer.get(0);
        else
            return new ChainedExchangeRateTimeSeries(answer.toArray(new ExchangeRateTimeSeries[0]));
    }

}
