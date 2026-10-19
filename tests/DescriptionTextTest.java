package ru.local.qcatalog;
import java.nio.charset.StandardCharsets;
public final class DescriptionTextTest {
    static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        require(DescriptionText.plain("<p>Test &amp; example</p><script>hidden</script>").equals("Test & example"),"HTML to text");
        String sample = "This is a sample game description. " + new String(new char[100]).replace("\0","Second sentence with emoji 🎮 and text. ");
        String brief = DescriptionText.brief(sample,450);
        require(brief.getBytes(StandardCharsets.UTF_8).length <= 450 && !brief.contains("�"),"UTF-8 request limit");
        require(DescriptionText.hasRussianText("Игра с управлением движениями рук и заданиями для одного игрока."),"Russian text bypasses translation");
        require(!DescriptionText.hasRussianText("A rhythm game with fast moving targets."),"English text needs translation");
        require(DescriptionText.brief("Short text.",450).equals("Short text."),"Short text retained");
        System.out.println("Description formatting tests passed (5 checks; no network requests)");
    }
}
