package pt.unl.fct.pds.model;

import java.util.Arrays;

public class Circuit {
    int id;
    Node[] nodes;
    int minBandwidth;

    public Circuit() {
    }

    public Circuit(
            int id,
            Node[] nodes,
            int minBandwidth) {
        this.id = id;
        this.nodes = Arrays.copyOf(nodes, nodes.length);
        this.minBandwidth = minBandwidth;
    }

    public int getId() {
        return id;
    }

    public Node[] getNodes() {
        return nodes;
    }

    public int getMinBandwidth() {
        return minBandwidth;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNodes(Node[] nodes) {
        this.nodes = Arrays.copyOf(nodes, nodes.length);
    }

    public void setMinBandwidthFromNodes() {
        int min = Integer.MAX_VALUE;
        for (Node n : nodes) {
            if (n.getBandwidth() < min) {
                min = n.getBandwidth();
            }
        }
        this.minBandwidth = (min == Integer.MAX_VALUE) ? 0 : min;
    }

}
