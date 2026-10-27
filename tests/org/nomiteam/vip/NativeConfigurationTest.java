package org.nomiteam.vip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.yaml.snakeyaml.Yaml;

/** Validate the shipped native action graph, including clicks with no inherited tier permissions. */
public final class NativeConfigurationTest {
    @SuppressWarnings("unchecked")
    private static List<String> execute(List<?> actions, Set<String> permissions) {
        List<String> commands = new ArrayList<>();
        for (Object action : actions) {
            if (action instanceof String command) {
                if (command.startsWith("console:")) {
                    commands.add(command);
                }
            } else {
                Map<String, Object> condition = (Map<String, Object>) action;
                String expression = (String) condition.get("condition");
                assert expression.startsWith("perm ") : expression;
                String branch = permissions.contains(expression.substring(5)) ? "actions" : "deny";
                commands.addAll(execute((List<?>) condition.get(branch), permissions));
            }
        }
        return commands;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<String, Object> menu;
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            menu = new Yaml().load(input);
        }
        Map<String, Object> icons = (Map<String, Object>) menu.get("Icons");
        String[] tierNodes = {"nuomi.vip.vip", "nuomi.vip.svip", "nuomi.vip.mvip"};
        String[] values = {"1.0", "0.9", "1.1", "0.8", "1.2", "0.7", "1.3", "0.6", "1.4"};
        int[] requiredTiers = {0, 1, 1, 1, 1, 2, 2, 3, 3};
        for (int mask = 0; mask < 8; mask++) {
            Set<String> permissions = new HashSet<>();
            int highestTier = 0;
            for (int bit = 0; bit < tierNodes.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    permissions.add(tierNodes[bit]);
                    highestTier = bit + 1;
                }
            }
            for (int index = 0; index < values.length; index++) {
                var icon = (Map<String, Object>) icons.get(String.valueOf((char) ('A' + index)));
                var actions = (Map<String, Object>) icon.get("actions");
                var commands = execute((List<?>) actions.get("all"), permissions);
                if (highestTier >= requiredTiers[index]) {
                    assert commands.equals(List.of("console: minecraft:attribute %player_name% minecraft:scale base set "
                            + values[index])) : "Wrong target, value or duplicate action: " + commands;
                } else {
                    assert commands.isEmpty() : "Unentitled click must not dispatch native attribute";
                }
            }
        }
        var flyIcon = (Map<String, Object>) icons.get("J");
        var flyActions = (Map<String, Object>) flyIcon.get("actions");
        assert flyActions.get("all").equals(List.of("command: nuomifly"));
        System.out.println("PASS native control YAML: nine scale values x eight tier combinations, single self target, "
                + "reset without permission, denied clicks and player-only fly action");
    }
}
