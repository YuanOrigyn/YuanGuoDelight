import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Prints the mod's lang key insertion order (which mirrors registrate registration order). */
public class ModOrder {
    public static void main(String[] args) throws Exception {
        JsonObject lang = JsonParser.parseString(Files.readString(Paths.get(args[0]), StandardCharsets.UTF_8)).getAsJsonObject();
        int i = 0;
        for (Map.Entry<String,JsonElement> e : lang.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("item.fruitsdelight.") && !k.startsWith("block.fruitsdelight.")) continue;
            String kind = k.startsWith("item.") ? "I" : "B";
            String id = k.substring(k.indexOf("fruitsdelight.") + "fruitsdelight.".length());
            System.out.printf("%3d %s %-38s %s%n", ++i, kind, id, e.getValue().getAsString());
        }
    }
}