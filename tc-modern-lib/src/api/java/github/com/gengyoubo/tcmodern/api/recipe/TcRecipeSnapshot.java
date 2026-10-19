package github.com.gengyoubo.tcmodern.api.recipe;

import java.util.List;

/** A snapshot owns no legacy references. Issues describe omitted unsupported or malformed recipes. */
public record TcRecipeSnapshot(List<TcRecipe> recipes, List<Issue> issues) {
    public record Issue(String source, String detail) { }
    public TcRecipeSnapshot {
        recipes = List.copyOf(recipes);
        issues = List.copyOf(issues);
    }
}
