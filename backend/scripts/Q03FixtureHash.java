class Q03FixtureHash {
    public static void main(String[] args) throws Exception {
        String raw = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
        System.out.print(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(raw,
                org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10)));
    }
}
