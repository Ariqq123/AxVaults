package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.items.component.DataComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ItemMatcher {
    private final ItemStack itemStack;
    private final WrappedItemStack wrapped;
    private final Map<String, Object> map;
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    private int needMatch = 0;

    public ItemMatcher(ItemStack itemStack, WrappedItemStack wrapped, Map<String, Object> map) {
        this.itemStack = itemStack;
        this.wrapped = wrapped;
        this.map = map;
    }

    public boolean isMatching() {
        int matches = 0;
        if (material()) matches++;
        if (name()) matches++;
        if (customModelData()) matches++;
        if (nbtValue()) matches++;
        return matches >= needMatch;
    }

    public boolean material() {
        Object val = map.getOrDefault("material", map.get("type"));
        if (val == null) return false;
        needMatch++;
        var material = DataComponents.material();
        if (material == null) return false;
        return SimpleRegex.matches((String) val, wrapped.get(material).toString());
    }

    public boolean name() {
        Object val = map.get("name");
        if (val == null) return false;
        needMatch++;
        Component customName = wrapped.get(DataComponents.customName());
        if (customName == null) return false;
        String plain = plainSerializer.serialize(customName);
        return SimpleRegex.matches((String) val, plain);
    }

    public boolean customModelData() {
        Object val = map.get("custom-model-data");
        if (!(val instanceof Integer num)) return false;
        needMatch++;
        var cmd = wrapped.get(DataComponents.customModelData());
        if (cmd == null || cmd.floats().isEmpty()) return false;
        return num == cmd.floats().getFirst().intValue();
    }

    public boolean nbtValue() {
        Object val = map.getOrDefault("nbt-value", map.get("nbt"));
        if (val == null) return false;
        needMatch++;

        if (itemStack == null) return false;

        Map<String, String> serialized = new LinkedHashMap<>();
        flatten(serialized, "", itemStack.serialize());
        String internal = findInternal(serialized);

        if (val instanceof String input) {
            String[] split = input.split("=", 2);
            if (split.length == 2) {
                return containsEntry(serialized, split[0], split[1], internal);
            }

            for (String value : serialized.values()) {
                if (SimpleRegex.matches(input, value)) return true;
            }

            return internal != null && SimpleRegex.matches(input, internal);
        }

        if (val instanceof Map<?, ?> tags) {
            for (Map.Entry<?, ?> entry : tags.entrySet()) {
                if (!containsEntry(serialized, Objects.toString(entry.getKey(), ""), Objects.toString(entry.getValue(), ""), internal)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private boolean containsEntry(Map<String, String> serialized, String keyRegex, String valueRegex, String internal) {
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            if (!SimpleRegex.matches(keyRegex, entry.getKey())) continue;
            if (SimpleRegex.matches(valueRegex, entry.getValue())) return true;
        }

        if (internal == null) return false;
        String simpleKey = keyRegex.replace("*", "");
        int index = internal.toLowerCase().indexOf(simpleKey.toLowerCase());
        if (index < 0) return false;
        String sliced = internal.substring(index);
        return SimpleRegex.matches("*" + valueRegex + "*", sliced);
    }

    private String findInternal(Map<String, String> serialized) {
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            if (!entry.getKey().toLowerCase().endsWith("internal")) continue;
            return entry.getValue();
        }
        return null;
    }

    private void flatten(Map<String, String> output, String parent, Object object) {
        if (object instanceof Map<?, ?> currentMap) {
            for (Map.Entry<?, ?> entry : currentMap.entrySet()) {
                String key = parent.isEmpty() ? Objects.toString(entry.getKey(), "") : parent + "." + Objects.toString(entry.getKey(), "");
                flatten(output, key, entry.getValue());
            }
            return;
        }

        if (object instanceof List<?> list) {
            List<String> converted = new ArrayList<>();
            for (Object element : list) {
                converted.add(Objects.toString(element, ""));
            }
            output.put(parent, String.join(",", converted));
            return;
        }

        output.put(parent, Objects.toString(object, ""));
    }
}
