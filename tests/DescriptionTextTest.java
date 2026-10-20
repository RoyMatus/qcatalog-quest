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
        require(DescriptionText.preferred("English source.","Русское описание.").equals("Русское описание."),"Available Russian wins over English");
        require(DescriptionText.preferred("English source.","").equals("English source."),"Unavailable translation shows English directly");
        require(DescriptionText.preferred("English source.","  ").equals("English source."),"Empty translation cannot hide the source");
        require(DescriptionText.preferred("","").isEmpty(),"Absent description stays empty without service notices");
        require(DescriptionText.plain("<a href=\"https://example.com/help?a=1&amp;b=2\"><b>Help</b></a>").equals("Help (https://example.com/help?a=1&b=2)"),"HTML link destinations survive cleanup");
        require(DescriptionText.plain("<a href='javascript:alert(1)'>Label</a>").equals("Label"),"Non-web HTML targets are not preserved");
        System.out.println("Description formatting tests passed (11 checks; no network requests)");
    }
}
