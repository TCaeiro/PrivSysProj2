package pt.unl.fct.pds.utils;

import pt.unl.fct.pds.model.Node;
import java.util.ArrayList;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.Map;

public class ConsensusParser {

    // Link do consenso
    private static final String CONSENSUS_URL = "http://217.196.147.77/tor/status-vote/current/consensus";

    // Cache para não fazermos pedidos repetidos para o mesmo IP
    private final Map<String, String> countryCache = new HashMap<>();

    private static final DateTimeFormatter CONSENSUS_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    // Construtor vazio
    public ConsensusParser() {
    }

    /**
     * Faz lookup do país de um IP usando o serviço gratuito ipinfo.io.
     * Exemplo: https://ipinfo.io/8.8.8.8/country -> "US\n"
     *
     * NOTA IMPORTANTE:
     * - Este método faz chamadas HTTP, por isso é relativamente lento.
     * - Usamos um cache (countryCache) para não repetir chamadas para o mesmo IP.
     * - Em caso de erro (timeout, rate limit, etc.), devolvemos "XX".
     */
    public String lookupCountry(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "XX";
        }

        // Verifica cache primeiro
        if (countryCache.containsKey(ip)) {
            return countryCache.get(ip);
        }

        String country = "XX"; // valor default

        BufferedReader reader = null;
        try {
            URL url = new URL("https://ipinfo.io/" + ip + "/country");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    country = line.trim();
                }
            } else {
                System.out.println("GeoIP falhou para IP " + ip + " (HTTP " + status + ")");
            }

        } catch (Exception e) {
            System.out.println("Erro no GeoIP lookup para IP " + ip + ": " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

        countryCache.put(ip, country);
        return country;
    }

    // Método para ler o consenso e retornar nodes
    public Node[] parseConsensus() {
        // Lista onde vamos guardar os nodes
        ArrayList<Node> nodeList = new ArrayList<>();

        try {
            // Faz a conexão HTTP para baixar o consenso
            System.out.println("A começar download do consenso...");
            URL url = new URL(CONSENSUS_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Lê o conteúdo do arquivo de consenso
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            Node current = null;

            // Lê cada linha do consenso
            while ((line = br.readLine()) != null) {
                // Imprime a linha para ver o que está a ser lido
                System.out.println("Lendo linha: " + line);

                // Se a linha começar com "r ", é o início de um novo node
                if (line.startsWith("r ")) {
                    // Se já tivermos um node, guarda ele na lista
                    if (current != null) {
                        current.setCountry("XX");
                        System.out.println("Node encontrado, adicionando à lista...");
                        nodeList.add(current);
                    }

                    // Cria um novo node
                    current = new Node();
                    System.out.println("Criando novo node...");

                    // Divide a linha para pegar as partes
                    String[] parts = line.split(" ");
                    System.out.println("Partes da linha (nickname, fingerprint, etc): " + String.join(", ", parts));

                    // Preenche os dados do node com as partes extraídas
                    current.setNickname(parts[1]);
                    current.setFingerprint(parts[2]);

                    // Publicação -> converte para LocalDateTime
                    String pubTimeRaw = parts[4] + " " + parts[5];
                    try {
                        LocalDateTime pubTime = LocalDateTime.parse(pubTimeRaw, CONSENSUS_TIME_FORMATTER);
                        current.setTimePublished(pubTime);
                        System.out.println("Data de publicação formatada: " + pubTime);
                    } catch (Exception e) {
                        System.out.println("Falha a fazer parse da data de publicação: " + pubTimeRaw);
                    }

                    // Preenche IP e portas
                    current.setIpAddress(parts[6]);
                    current.setOrPort(Integer.parseInt(parts[7]));
                    current.setDirPort(Integer.parseInt(parts[8]));
                    System.out.println("IP: " + parts[6] + ", OR Port: " + parts[7] + ", DIR Port: " + parts[8]);
                }

                // "s " -> flags
                else if (line.startsWith("s ") && current != null) {
                    String[] flags = line.substring(2).split(" ");
                    current.setFlags(flags);
                    System.out.println("Flags encontradas: " + String.join(", ", flags));
                }

                // "v " -> versão
                else if (line.startsWith("v ") && current != null) {
                    current.setVersion(line.substring(2));
                    System.out.println("Versão: " + line.substring(2));
                }

                // "w " -> largura de banda (bandwidth)
                else if (line.startsWith("w ") && current != null) {
                    // Linha típica: "w Bandwidth=12345 Unmeasured=1"
                    String[] parts = line.substring(2).trim().split(" ");
                    for (String part : parts) {
                        if (part.startsWith("Bandwidth=")) {
                            String bwVal = part.substring("Bandwidth=".length());
                            current.setBandwidth(Integer.parseInt(bwVal));
                            System.out.println("Bandwidth: " + bwVal);
                            break;
                        }
                    }
                }

                // "p " -> exit policy
                else if (line.startsWith("p ") && current != null) {
                    current.setExitPolicy(line.substring(2));
                    System.out.println("Exit Policy: " + line.substring(2));
                }
            }

            // Após ler todas as linhas, adiciona o último node
            if (current != null) {
                current.setCountry("XX");
                System.out.println("Último node encontrado, adicionando à lista...");
                nodeList.add(current);
            }

            // Fecha o BufferedReader
            br.close();
            System.out.println("Leitura concluída!");

        } catch (Exception e) {
            System.out.println("Erro ao processar o consenso.");
            e.printStackTrace();

            if (!nodeList.isEmpty()) {
                System.out.println("Aviso: conexão caiu, mas já foram lidos "
                        + nodeList.size() + " nodes. A usar estes nodes.");
                return nodeList.toArray(new Node[0]);
            }

            return null;
        }

        // Retorna a lista de nodes como um array
        System.out.println("Retornando " + nodeList.size() + " nodes.");
        return nodeList.toArray(new Node[0]);
    }

    // Método main para testar o código
    public static void main(String[] args) {
        // Cria a instância do parser
        ConsensusParser parser = new ConsensusParser();

        // Chama o método parseConsensus e obtém os nodes
        Node[] nodes = parser.parseConsensus();

        // Se os nodes forem nulos, exibe erro
        if (nodes == null) {
            System.out.println("Erro ao processar o consenso.");
            return;
        }

        // Imprime os dados de cada node
        System.out.println("Nodes extraídos do consenso:");
        for (Node node : nodes) {
            System.out.println("Nickname : " + node.getNickname());
            System.out.println("Fingerprint : " + node.getFingerprint());
            System.out.println("Time Published : " + node.getTimePublished());
            System.out.println("IP Address : " + node.getIpAddress());
            System.out.println("OR Port : " + node.getOrPort());
            System.out.println("DIR Port : " + node.getDirPort());
            System.out.println("Version : " + node.getVersion());
            System.out.println("Bandwidth : " + node.getBandwidth());
            System.out.println("Country : " + node.getCountry());
            System.out.println("Exit Policy : " + node.getExitPolicy());
            System.out.println("---------------");
        }
    }
}
