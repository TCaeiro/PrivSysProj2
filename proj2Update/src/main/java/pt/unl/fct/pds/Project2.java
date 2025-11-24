package pt.unl.fct.pds;

import pt.unl.fct.pds.model.Node;
import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.utils.ConsensusParser;

/**
 * Application for Tor Path Selection alternatives.
 */
public class Project2 {
    public static void main(String[] args) {
        System.out.println("Welcome to the Circuit Simulator!");

        // 1) Parse consensus
        ConsensusParser parser = new ConsensusParser();
        Node[] nodes = parser.parseConsensus();
        if (nodes == null || nodes.length == 0) {
            System.out.println("Erro: não foram encontrados nodes no consenso.");
            return;
        }
        System.out.println("Total de nodes carregados: " + nodes.length);

        // 2) Create path selector (baseline)
        PathSelector selector = new PathSelector(nodes);

        // 3) Select circuits
        Circuit c1 = selector.selectPathBaseline(1); // baseline algorithm
        Circuit c2 = selector.selectPathGeoAware(2, 0.5, 0.2); // geo-aware algorithm

        // 4) Print BASELINE circuit info
        System.out.println("\n=== Circuito escolhido (baseline) ===");
        printCircuit(parser, c1);

        // 5) Print GEO-AWARE circuit info
        System.out.println("\n=== Circuito escolhido (geo-aware) ===");
        printCircuit(parser, c2);

        // --- EXPERIMENT / SIMULATION

        int numCircuits = 20;
        double alpha = 0.5;
        double beta = 0.2;

        System.out.println("\nA correr simulação com " + numCircuits + " circuitos...");

        ExperimentResults baselineRes = runExperiment(parser, selector, numCircuits, "baseline", alpha, beta);
        ExperimentResults geoRes = runExperiment(parser, selector, numCircuits, "geo", alpha, beta);

        System.out.println("\n=== Distinct nodes used (baseline) ===");
        System.out.println("Total unique nodes: " + baselineRes.allNodes.size());
        System.out.println("Guards:  " + baselineRes.guards.size());
        System.out.println("Middles: " + baselineRes.middles.size());
        System.out.println("Exits:   " + baselineRes.exits.size());

        System.out.println("\n=== Distinct nodes used (geo-aware) ===");
        System.out.println("Total unique nodes: " + geoRes.allNodes.size());
        System.out.println("Guards:  " + geoRes.guards.size());
        System.out.println("Middles: " + geoRes.middles.size());
        System.out.println("Exits:   " + geoRes.exits.size());

        // Entropy: note total selections = 3 * numCircuits for global; numCircuits per
        // position
        int totalSelections = 3 * numCircuits;

        double H_all_baseline = computeEntropy(baselineRes.allCountries, totalSelections);
        double H_guard_baseline = computeEntropy(baselineRes.guardCountries, numCircuits);
        double H_middle_baseline = computeEntropy(baselineRes.middleCountries, numCircuits);
        double H_exit_baseline = computeEntropy(baselineRes.exitCountries, numCircuits);

        double H_all_geo = computeEntropy(geoRes.allCountries, totalSelections);
        double H_guard_geo = computeEntropy(geoRes.guardCountries, numCircuits);
        double H_middle_geo = computeEntropy(geoRes.middleCountries, numCircuits);
        double H_exit_geo = computeEntropy(geoRes.exitCountries, numCircuits);

        System.out.println("\n=== Shannon entropy of country selection (baseline) ===");
        System.out.println("Global: " + H_all_baseline);
        System.out.println("Guard:  " + H_guard_baseline);
        System.out.println("Middle: " + H_middle_baseline);
        System.out.println("Exit:   " + H_exit_baseline);

        System.out.println("\n=== Shannon entropy of country selection (geo-aware) ===");
        System.out.println("Global: " + H_all_geo);
        System.out.println("Guard:  " + H_guard_geo);
        System.out.println("Middle: " + H_middle_geo);
        System.out.println("Exit:   " + H_exit_geo);

    }

    // Helper to print circuits
    private static void printCircuit(ConsensusParser parser, Circuit c) {
        Node[] nodes = c.getNodes();

        String guardCountry = ensureCountry(parser, nodes[0]);
        String middleCountry = ensureCountry(parser, nodes[1]);
        String exitCountry = ensureCountry(parser, nodes[2]);

        System.out.println("Guard:  " + nodes[0].getNickname() + " ("
                + nodes[0].getIpAddress() + ", "
                + guardCountry + ")");

        System.out.println("Middle: " + nodes[1].getNickname() + " ("
                + nodes[1].getIpAddress() + ", "
                + middleCountry + ")");

        System.out.println("Exit:   " + nodes[2].getNickname() + " ("
                + nodes[2].getIpAddress() + ", "
                + exitCountry + ")");

        System.out.println("Circuit min bandwidth: " + c.getMinBandwidth());
    }

    static class ExperimentResults {
        int numCircuits;
        java.util.Set<String> guards = new java.util.HashSet<>();
        java.util.Set<String> middles = new java.util.HashSet<>();
        java.util.Set<String> exits = new java.util.HashSet<>();
        java.util.Set<String> allNodes = new java.util.HashSet<>();

        java.util.Map<String, Integer> guardCountries = new java.util.HashMap<>();
        java.util.Map<String, Integer> middleCountries = new java.util.HashMap<>();
        java.util.Map<String, Integer> exitCountries = new java.util.HashMap<>();
        java.util.Map<String, Integer> allCountries = new java.util.HashMap<>();

        java.util.List<Integer> circuitBandwidths = new java.util.ArrayList<>();
    }

    private static void incCount(java.util.Map<String, Integer> map, String key) {
        if (key == null)
            key = "UNKNOWN";
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private static double computeEntropy(java.util.Map<String, Integer> counts, int total) {
        if (total == 0)
            return 0.0;
        double h = 0.0;
        for (int count : counts.values()) {
            double p = (double) count / (double) total;
            if (p > 0.0) {
                h -= p * (Math.log(p) / Math.log(2));
            }
        }
        return h;
    }

    private static ExperimentResults runExperiment(ConsensusParser parser,
            PathSelector selector,
            int numCircuits,
            String type,
            double alpha,
            double beta) {
        ExperimentResults res = new ExperimentResults();
        res.numCircuits = numCircuits;

        for (int i = 0; i < numCircuits; i++) {
            Circuit c;
            if ("baseline".equalsIgnoreCase(type)) {
                c = selector.selectPathBaseline(i);
            } else {
                c = selector.selectPathGeoAware(i, alpha, beta);
            }

            Node[] nodes = c.getNodes();
            Node guard = nodes[0];
            Node middle = nodes[1];
            Node exit = nodes[2];

            // fingerprints (unique nodes)
            res.guards.add(guard.getFingerprint());
            res.middles.add(middle.getFingerprint());
            res.exits.add(exit.getFingerprint());
            res.allNodes.add(guard.getFingerprint());
            res.allNodes.add(middle.getFingerprint());
            res.allNodes.add(exit.getFingerprint());

            String gCountry = ensureCountry(parser, guard);
            String mCountry = ensureCountry(parser, middle);
            String eCountry = ensureCountry(parser, exit);

            incCount(res.guardCountries, gCountry);
            incCount(res.middleCountries, mCountry);
            incCount(res.exitCountries, eCountry);
            incCount(res.allCountries, gCountry);
            incCount(res.allCountries, mCountry);
            incCount(res.allCountries, eCountry);

            res.circuitBandwidths.add(c.getMinBandwidth());
        }

        return res;
    }

    private static String ensureCountry(ConsensusParser parser, Node n) {
        String c = n.getCountry();
        if (c != null && !"XX".equals(c) && !"".equals(c)) {
            return c;
        }

        String ip = n.getIpAddress();
        if (ip == null || ip.isEmpty()) {
            c = "XX";
        } else {
            c = parser.lookupCountry(ip);
        }

        n.setCountry(c);
        return c;
    }
}