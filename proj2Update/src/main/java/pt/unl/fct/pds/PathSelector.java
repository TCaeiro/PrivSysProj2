package pt.unl.fct.pds;

import pt.unl.fct.pds.model.Node;
import pt.unl.fct.pds.model.Circuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PathSelector {

    private final Node[] allNodes;
    private final Random random = new Random();

    public PathSelector(Node[] allNodes) {
        this.allNodes = allNodes;
    }

    public Circuit selectPathBaseline(int circuitId) {
        Node exit = selectExit();
        Node guard = selectGuard(exit);
        Node middle = selectMiddle(guard, exit);

        Node[] nodes = new Node[] { guard, middle, exit };
        int minBw = computeMinBandwidth(nodes);

        return new Circuit(circuitId, nodes, minBw);
    }

    private Node selectExit() {
        List<Node> candidates = new ArrayList<>();
        for (Node n : allNodes) {
            if (n == null)
                continue;
            if (!n.isFast())
                continue;
            if (!isSuitableExit(n))
                continue;
            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable exit nodes found!");
        }

        return weightedRandomByBandwidth(candidates);
    }

    private boolean isSuitableExit(Node n) {
        String policy = n.getExitPolicy();
        if (policy == null)
            return true;
        policy = policy.trim().toLowerCase();
        return !policy.startsWith("reject *:*");
    }

    private Node selectGuard(Node exit) {
        List<Node> candidates = new ArrayList<>();
        for (Node n : allNodes) {
            if (n == null)
                continue;
            if (!n.isGuard())
                continue;

            if (same16Subnet(n, exit))
                continue;

            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable guard nodes found!");
        }

        return weightedRandomByBandwidth(candidates);
    }

    private Node selectMiddle(Node guard, Node exit) {
        List<Node> candidates = new ArrayList<>();
        for (Node n : allNodes) {
            if (n == null)
                continue;
            if (!n.isFast())
                continue;

            if (same16Subnet(n, exit) || same16Subnet(n, guard))
                continue;

            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable middle nodes found!");
        }

        return weightedRandomByBandwidth(candidates);
    }

    private Node weightedRandomByBandwidth(List<Node> candidates) {
        long total = 0;
        for (Node n : candidates) {
            int bw = n.getBandwidth();
            if (bw > 0) {
                total += bw;
            }
        }

        if (total <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        long r = (long) (random.nextDouble() * total);
        long cumulative = 0;

        for (Node n : candidates) {
            int bw = n.getBandwidth();
            if (bw > 0) {
                cumulative += bw;
                if (cumulative > r) {
                    return n;
                }
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private boolean same16Subnet(Node a, Node b) {
        if (a == null || b == null)
            return false;
        String ipa = a.getIpAddress();
        String ipb = b.getIpAddress();
        if (ipa == null || ipb == null)
            return false;

        String[] pa = ipa.split("\\.");
        String[] pb = ipb.split("\\.");
        if (pa.length < 2 || pb.length < 2)
            return false;

        return pa[0].equals(pb[0]) && pa[1].equals(pb[1]);
    }

    private int computeMinBandwidth(Node[] nodes) {
        int min = Integer.MAX_VALUE;
        for (Node n : nodes) {
            if (n == null)
                continue;
            if (n.getBandwidth() < min) {
                min = n.getBandwidth();
            }
        }
        return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    /**
     * Generic weighted random selection given an explicit weight for each
     * candidate.
     * weights[i] corresponds to candidates.get(i).
     */
    private Node weightedRandomWithWeights(List<Node> candidates, double[] weights) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidates to choose from.");
        }
        if (weights.length != candidates.size()) {
            throw new IllegalArgumentException("weights length must match candidates size.");
        }

        double total = 0.0;
        for (double w : weights) {
            if (w > 0) {
                total += w;
            }
        }

        if (total <= 0.0) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        double r = random.nextDouble() * total;
        double cumulative = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            double w = Math.max(0.0, weights[i]);
            cumulative += w;
            if (cumulative > r) {
                return candidates.get(i);
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    public Circuit selectPathGeoAware(int circuitId, double alpha, double beta) {
        alpha = Math.max(0.0, Math.min(1.0, alpha));
        beta = Math.max(0.0, Math.min(1.0, beta));

        Node exit = selectExit();
        Node guard = selectGuardGeoAware(exit, alpha);
        Node middle = selectMiddleGeoAware(guard, exit, beta);

        Node[] nodes = new Node[] { guard, middle, exit };
        int minBw = computeMinBandwidth(nodes);

        return new Circuit(circuitId, nodes, minBw);
    }

    private Node selectGuardGeoAware(Node exit, double alpha) {
        List<Node> candidates = new ArrayList<>();

        for (Node n : allNodes) {
            if (n == null)
                continue;
            if (!n.isGuard())
                continue;

            if (same16Subnet(n, exit))
                continue;

            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable guard nodes found (geo-aware)!");
        }

        double[] weights = new double[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            Node n = candidates.get(i);
            int bw = n.getBandwidth();
            if (bw <= 0) {
                weights[i] = 0.0;
                continue;
            }

            String guardCountry = n.getCountry();
            String exitCountry = exit.getCountry();

            if (guardCountry != null && exitCountry != null && !guardCountry.equals(exitCountry)) {
                weights[i] = bw * (1.0 + alpha);
            } else {
                weights[i] = bw;
            }
        }

        return weightedRandomWithWeights(candidates, weights);
    }

    private Node selectMiddleGeoAware(Node guard, Node exit, double beta) {
        List<Node> candidates = new ArrayList<>();

        for (Node n : allNodes) {
            if (n == null)
                continue;
            if (!n.isFast())
                continue;

            if (same16Subnet(n, exit) || same16Subnet(n, guard))
                continue;

            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable middle nodes found (geo-aware)!");
        }

        double[] weights = new double[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            Node n = candidates.get(i);
            int bw = n.getBandwidth();
            if (bw <= 0) {
                weights[i] = 0.0;
                continue;
            }

            String mCountry = n.getCountry();
            String gCountry = guard.getCountry();
            String eCountry = exit.getCountry();

            int shared = 0;

            if (mCountry != null && gCountry != null && mCountry.equals(gCountry)) {
                shared++;
            }
            if (mCountry != null && eCountry != null && mCountry.equals(eCountry)) {
                shared++;
            }

            int c;
            if (shared == 2) {
                c = 1;
            } else if (shared == 1) {
                c = 2;
            } else {
                c = 3;
            }

            weights[i] = bw * (1.0 + beta * c);
        }

        return weightedRandomWithWeights(candidates, weights);
    }

}
