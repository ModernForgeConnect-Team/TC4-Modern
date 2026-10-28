package github.com.gengyoubo.tcmodern.tc4.practice;

import java.util.*;

/** A constructive generator: every retained cell has a compatible edge to its parent in a solution tree. */
public final class PracticePuzzle {
    public record Hex(int q, int r) {
        public List<Hex> neighbors() {
            return List.of(new Hex(q + 1, r), new Hex(q - 1, r), new Hex(q, r + 1),
                    new Hex(q, r - 1), new Hex(q + 1, r - 1), new Hex(q - 1, r + 1));
        }
        public int distance(Hex other) {
            int dq = q - other.q, dr = r - other.r;
            return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
        }
    }

    private final PracticeAspects aspects;
    private final int level;
    private final Map<Hex, String> solution;
    private final Map<Hex, String> anchors;
    private final Map<Hex, String> placed = new LinkedHashMap<>();

    private PracticePuzzle(PracticeAspects aspects, int level, Map<Hex, String> solution, Map<Hex, String> anchors) {
        this.aspects = aspects;
        this.level = level;
        this.solution = Collections.unmodifiableMap(new LinkedHashMap<>(solution));
        this.anchors = Collections.unmodifiableMap(new LinkedHashMap<>(anchors));
        reset();
    }

    public static PracticePuzzle generate(PracticeAspects aspects, int level, Random random) {
        PracticeAspects.checkLevel(level);
        var palette = aspects.available(level).stream().map(a -> a.tag()).toList();
        var links = new LinkedHashMap<String, List<String>>();
        for (String tag : palette) links.put(tag, palette.stream().filter(other -> aspects.connects(tag, other)).toList());
        var roots = palette.stream().filter(tag -> aspects.tier(tag) == level && !links.get(tag).isEmpty()).toList();
        if (roots.isEmpty()) throw new IllegalArgumentException("No connected aspects at tier " + level);

        int radius = level <= 2 ? 2 : 3;
        var cells = new ArrayList<Hex>();
        for (int q = -radius; q <= radius; q++) for (int r = -radius; r <= radius; r++)
            if (new Hex(q, r).distance(new Hex(0, 0)) <= radius) cells.add(new Hex(q, r));
        Collections.shuffle(cells, random);
        var root = cells.stream().filter(h -> h.distance(new Hex(0, 0)) == radius).findFirst().orElseThrow();

        // Spread fixed terminals apart so the initial board always needs player input.
        var terminals = new ArrayList<Hex>();
        terminals.add(root);
        while (terminals.size() < 3 + level / 3) {
            var next = cells.stream().filter(h -> !terminals.contains(h))
                    .max(Comparator.comparingInt(h -> terminals.stream().mapToInt(h::distance).min().orElse(0))).orElseThrow();
            terminals.add(next);
        }

        var solution = new LinkedHashMap<Hex, String>();
        var parents = new HashMap<Hex, Hex>();
        solution.put(root, roots.get(random.nextInt(roots.size())));
        var frontier = new ArrayList<Hex>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            var parent = frontier.remove(random.nextInt(frontier.size()));
            var neighbors = new ArrayList<>(parent.neighbors());
            Collections.shuffle(neighbors, random);
            for (var cell : neighbors) {
                if (!cells.contains(cell) || solution.containsKey(cell)) continue;
                var choices = links.get(solution.get(parent));
                solution.put(cell, choices.get(random.nextInt(choices.size())));
                parents.put(cell, parent);
                frontier.add(cell);
            }
        }
        // Removing only non-terminal leaves preserves the solution joining every fixed terminal.
        for (int i = 0; i < level - 1; i++) {
            var leaves = solution.keySet().stream().filter(h -> !terminals.contains(h) && !parents.containsValue(h)).toList();
            if (leaves.isEmpty()) break;
            var leaf = leaves.get(random.nextInt(leaves.size()));
            solution.remove(leaf);
            parents.remove(leaf);
        }
        var anchors = new LinkedHashMap<Hex, String>();
        terminals.forEach(h -> anchors.put(h, solution.get(h)));
        return new PracticePuzzle(aspects, level, solution, anchors);
    }

    public int level() { return level; }
    public Set<Hex> cells() { return solution.keySet(); }
    public Map<Hex, String> anchors() { return anchors; }
    public Map<Hex, String> solution() { return solution; }
    public Map<Hex, String> placed() { return Collections.unmodifiableMap(placed); }
    public void reset() { placed.clear(); placed.putAll(anchors); }

    public boolean place(Hex cell, String tag) {
        if (!solution.containsKey(cell) || anchors.containsKey(cell) || aspects.tier(tag) > level) return false;
        placed.put(cell, tag);
        return true;
    }

    public boolean remove(Hex cell) {
        return !anchors.containsKey(cell) && placed.remove(cell) != null;
    }

    public Set<Hex> connected() { return connected(placed); }

    private Set<Hex> connected(Map<Hex, String> values) {
        var visited = new HashSet<Hex>();
        var queue = new ArrayDeque<Hex>();
        var first = anchors.keySet().iterator().next();
        visited.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            var current = queue.remove();
            for (var neighbor : current.neighbors())
                if (!visited.contains(neighbor) && aspects.connects(values.get(current), values.get(neighbor))) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
        }
        return visited;
    }

    /** Any valid network wins; matching the generated solution or filling the board is unnecessary. */
    public boolean solved() { return connected().containsAll(anchors.keySet()); }

    /** Extend the generated solution from the first terminal, correcting a conflicting placement when needed. */
    public Optional<Hex> hint() {
        var reached = connected(solution);
        var queue = new ArrayDeque<Hex>();
        var visited = new HashSet<Hex>();
        queue.add(anchors.keySet().iterator().next());
        while (!queue.isEmpty()) {
            var cell = queue.remove();
            if (!visited.add(cell)) continue;
            if (!Objects.equals(placed.get(cell), solution.get(cell))) {
                placed.put(cell, solution.get(cell));
                return Optional.of(cell);
            }
            for (var neighbor : cell.neighbors())
                if (reached.contains(neighbor) && aspects.connects(solution.get(cell), solution.get(neighbor))) queue.add(neighbor);
        }
        return Optional.empty();
    }
}
