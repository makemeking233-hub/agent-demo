package com.example.agent.cli;

import org.jline.builtins.Completers;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.StringsCompleter;

import java.util.List;

/** JLine3 命令补全（/help /clear /quit /history）。 */
public class Completion {
    public static Completer build(List<String> commands) {
        return new StringsCompleter(commands);
    }
}