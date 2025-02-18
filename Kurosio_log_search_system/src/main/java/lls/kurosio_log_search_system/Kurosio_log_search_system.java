package lls.kurosio_log_search_system;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Kurosio_log_search_system extends JavaPlugin implements TabExecutor, TabCompleter {

    @Override
    public void onEnable() {
        getLogger().info("Kurosio Log Search System プラグインが有効化しました。");
        Objects.requireNonNull(getCommand("lss")).setExecutor(this);
        Objects.requireNonNull(getCommand("lss")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "引数が不足しています！コマンドの形式を確認してください。");
            return false;
        }

        Map<String, String> options = parseArguments(args);

        // 必須引数のチェック
        if (!options.containsKey("p") || !options.containsKey("u") || !options.containsKey("pl")) {
            sender.sendMessage(ChatColor.RED + "必要な引数(p, u, pl)が不足しています！");
            return false;
        }

        String period = options.get("p");
        String users = options.get("u");
        String plugin = options.get("pl");
        String display = options.getOrDefault("d", "latest=50");
        String fileOption = options.getOrDefault("f", "not");
        String lognumber = options.getOrDefault("lm", "1");

        try {
            List<String> logLines = fetchLogLines(period, plugin, users, lognumber);
            int displayLimit = Integer.parseInt(display.replace("latest=", ""));
            boolean saveFile = fileOption.equalsIgnoreCase("yes");

            if (logLines.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "指定した条件で一致するログが見つかりませんでした。");
                return true;
            }

            // ログの表示と保存
            displayLogs(sender, logLines, displayLimit, saveFile, options);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "表示数の形式が正しくありません: " + display);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "エラーが発生しました: " + e.getMessage());
            getLogger().severe("エラー詳細: " + e);
        }

        return true;
    }

    private Map<String, String> parseArguments(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            String[] parts = arg.split(":", 2);
            if (parts.length == 2) {
                options.put(parts[0].toLowerCase(), parts[1]);
            }
        }
        return options;
    }

    private List<String> fetchLogLines(String period, String plugin, String users, String lognumber) throws IOException {
        List<String> matchingLines = new ArrayList<>();
        List<Path> logFiles = getLogFiles(period, lognumber);

        for (Path logFile : logFiles) {
            List<String> lines = readLogFile(logFile);
            if ("tradeplus".equalsIgnoreCase(plugin) || "shopchest".equalsIgnoreCase(plugin) || "crazyauctions".equalsIgnoreCase(plugin)) {
                matchingLines.addAll(fetchPluginLogs(lines, users, plugin));
            } else {
                for (String line : lines) {
                    if (line.toLowerCase().contains("[" + plugin.toLowerCase() + "]")) {
                        // 特定のユーザー名を含む場合のみ追加
                        if (containsUser(line, users)) {
                            matchingLines.add(line);
                        }
                    }
                }
            }
        }

        return matchingLines;
    }

    // 共通の処理をまとめたメソッド
    private List<String> fetchPluginLogs(List<String> lines, String users, String plugin) {
        List<String> collectedLogs = new ArrayList<>();
        Set<String> timestamps = new HashSet<>();

        // 特定ユーザーに一致するログの時刻を取得
        for (String line : lines) {
            if (containsUser(line, users) && line.toLowerCase().contains("[" + plugin.toLowerCase() + "]")) {
                String timestamp = extractTimestamp(line);
                if (timestamp != null) {
                    timestamps.add(timestamp);
                }
            }
        }

        // 同じ時刻に属するすべてのログを収集
        for (String line : lines) {
            String timestamp = extractTimestamp(line);
            if (timestamp != null && timestamps.contains(timestamp)) {
                if (line.toLowerCase().contains("[" + plugin.toLowerCase() + "]")) {
                    collectedLogs.add(line);
                }
            }
        }

        return collectedLogs;
    }

    private String extractTimestamp(String line) {
        // タイムスタンプ抽出用の正規表現
        Pattern pattern = Pattern.compile("\\[(\\d{2}:\\d{2}:\\d{2})\\]");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 正規表現に失敗した場合のフォールバック処理
        int startIndex = line.indexOf("[");
        int endIndex = line.indexOf("]");
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return line.substring(startIndex + 1, endIndex);
        }

        return null;
    }

    private boolean containsUser(String line, String users) {
        return Arrays.stream(users.split(","))
                .anyMatch(line::contains);
    }

    private List<Path> getLogFiles(String period, String lognumber) {
        Path logsDir = Paths.get(getDataFolder().toString(), "logs");
        if (!Files.exists(logsDir)) {
            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                getLogger().warning("logsディレクトリの作成中にエラーが発生しました: " + e.getMessage());
            }
        }

        List<Path> logFiles = new ArrayList<>();

        // p:latest の場合、latest.logを参照
        if ("latest".equalsIgnoreCase(period)) {
            Path latestLogPath = logsDir.resolve("latest.log");
            if (Files.exists(latestLogPath)) {
                logFiles.add(latestLogPath);
            } else {
                getLogger().warning("logsディレクトリ内にlatest.logが見つかりませんでした。");
            }
        } else {
            // p:latest以外の場合の既存処理
            String logFilePattern = period + (lognumber != null && !lognumber.isEmpty() ? "-" + lognumber : "") + ".log.gz";
            try {
                Files.walk(logsDir)
                        .filter(file -> file.toString().endsWith(".log.gz"))
                        .filter(file -> file.getFileName().toString().equals(logFilePattern))
                        .forEach(file -> {
                            Path extractedFile = extractLogGzFile(file);
                            logFiles.add(extractedFile);
                        });
            } catch (IOException e) {
                getLogger().warning("logsディレクトリ内のファイル探索中にエラーが発生しました: " + e.getMessage());
            }
        }

        return logFiles;
    }

    private Path extractLogGzFile(Path gzFilePath) {
        try {
            Path tempDir = Files.createTempDirectory("lss_temp");
            Path outputFile = tempDir.resolve(gzFilePath.getFileName().toString().replace(".gz", ""));

            try (InputStream is = new GZIPInputStream(Files.newInputStream(gzFilePath));
                 OutputStream os = Files.newOutputStream(outputFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }
            return outputFile;
        } catch (IOException e) {
            getLogger().warning("解凍中にエラーが発生しました: " + e.getMessage());
            return null;
        }
    }

    private List<String> readLogFile(Path logFile) throws IOException {
        if (logFile.toString().endsWith(".tar")) {
            return readTarFile(logFile);
        } else {
            return Files.readAllLines(logFile, StandardCharsets.UTF_8);
        }
    }

    private List<String> readTarFile(Path tarFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream is = Files.newInputStream(tarFile);
             TarArchiveInputStream tis = new TarArchiveInputStream(is)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.isFile()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(tis, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }

    private void displayLogs(CommandSender sender, List<String> logLines, int limit, boolean saveFile, Map<String, String> options) {
        sender.sendMessage(ChatColor.AQUA + "LSS ログ検索結果:");

        int displayCount = Math.min(logLines.size(), limit);
        for (int i = 0; i < displayCount; i++) {
            sender.sendMessage(ChatColor.GRAY + logLines.get(i));
        }

        if (saveFile) {
            String logFileName = options.get("p") + (options.containsKey("lm") ? "-" + options.get("lm") : "") + ".log";
            saveLogsToFile(sender, logLines, logFileName, options.get("u"), options.get("pl"));
        }
    }

    private void saveLogsToFile(CommandSender sender, List<String> logLines, String logFileName, String users, String plugin) {
        Path folderPath = Paths.get(getDataFolder().toString(), "lssresult");
        try {
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            // ファイル名生成
            String baseFileName = "LSS-" + logFileName.replace(".log", ""); // LSS-2025-01-09-1
            int sequence = 1;
            Path savePath;

            // 重複したファイル名に対応
            do {
                String fullFileName = baseFileName + "-" + sequence + ".txt";
                savePath = folderPath.resolve(fullFileName);
                sequence++;
            } while (Files.exists(savePath)); // ファイルが存在しない名前になるまで増加

            // ファイル内容を整形して保存
            List<String> fileContent = new ArrayList<>();
            fileContent.add("LSS取得結果");
            fileContent.add("取得ファイル名：" + logFileName);
            fileContent.add("ユーザー名：" + users);
            fileContent.add("プラグイン：" + plugin);
            fileContent.add("");
            fileContent.add("<取得結果>");
            fileContent.addAll(logLines);

            Files.write(savePath, fileContent, StandardCharsets.UTF_8);

            sender.sendMessage(ChatColor.GREEN + "ログを保存しました: " + savePath.toAbsolutePath());
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "ファイル保存中にエラーが発生しました: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // 最初に p:, lm:, u:, pl:, f: の順で出す
            suggestions.add("p:");
            suggestions.add("lm:");
            suggestions.add("u:");
            suggestions.add("pl:");
            suggestions.add("f:");
        } else if (args.length == 2) {
            if ("p:".equals(args[0])) {
                suggestions.add("latest"); // p: の補完は "latest"
            } else if ("lm:".equals(args[0])) {
                // lm: に関連する補完が必要ならここで追加
            } else if ("u:".equals(args[0])) {
                // ユーザー名補完処理（必要なら）
            }
        } else if (args.length == 3) {
            if ("pl:".equals(args[0])) {
                suggestions.add("TradePlus");
                suggestions.add("ShopChest");
                suggestions.add("CrazyAuctions");
                suggestions.add("RyuZUPluginChat");
            }
        } else if (args.length == 4) {
            if ("f:".equals(args[0])) {
                suggestions.add("yes");
                suggestions.add("not");
            }
        }

        return suggestions;
    }

}