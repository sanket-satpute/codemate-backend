import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class TestApis {
    public static void main(String[] args) throws Exception {
        Class<?> apiClass = Class.forName("org.springframework.ai.openai.api.OpenAiApi");
        System.out.println("--- API Constructors ---");
        for (Constructor<?> c : apiClass.getConstructors()) {
            System.out.println(c);
        }

        Class<?> optionsClass = Class.forName("org.springframework.ai.openai.OpenAiChatOptions");
        System.out.println("--- Options Methods ---");
        for (Method m : optionsClass.getMethods()) {
            if (m.getName().toLowerCase().contains("model")) {
                System.out.println(m);
            }
        }
    }
}
