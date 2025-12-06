public class TestChinese {
    public static void main(String[] args) {
        String[] tests = {"七月", "跑腿", "TEST123", "ZTA601TAB2521446PX", "test中文mix"};
        for (String test : tests) {
            boolean containsChinese = test.matches(".*[\\u4e00-\\u9fa5]+.*");
            System.out.println(test + " -> contains Chinese: " + containsChinese);
        }
    }
}
