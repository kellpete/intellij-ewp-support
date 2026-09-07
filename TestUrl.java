import java.net.URL;
import java.net.HttpURLConnection;

public class TestUrl {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://download.jetbrains.com/idea/ideaIC-2024.1.4.tar.gz");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        System.out.println("Response Code: " + conn.getResponseCode());
        System.out.println("Response Message: " + conn.getResponseMessage());
    }
}
