package dev.riffedcheats;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Brigadier tab-completion, same engine as vanilla slash-command suggestions. */
public final class Suggest {
    public static <S> void with(CommandDispatcher<S> d, S source, String text, Consumer<List<String>> cb) {
        try {
            String t = text.startsWith("/") ? text.substring(1) : text;
            ParseResults<S> pr = d.parse(t, source);
            d.getCompletionSuggestions(pr, t.length()).thenAccept(sug -> {
                List<String> out = new ArrayList<>();
                for (Suggestion s : sug.getList()) {
                    out.add(s.apply(t));
                    if (out.size() >= 40) break;
                }
                cb.accept(out);
            });
        } catch (Throwable ignored) {
            cb.accept(List.of());
        }
    }
}
