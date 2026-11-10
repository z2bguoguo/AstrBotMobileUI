import java.nio.file.*;
import java.security.*;
import java.util.*;
/** Signs exact bundle bytes using the APK's persistent key; secrets are read only from env. */
class SignCore {
  public static void main(String[] args) throws Exception {
    KeyStore store=KeyStore.getInstance("JKS");
    try(var in=Files.newInputStream(Path.of(System.getenv("SIGNING_STORE_FILE")))) {
      store.load(in,System.getenv("SIGNING_STORE_PASSWORD").toCharArray());
    }
    PrivateKey key=(PrivateKey)store.getKey(System.getenv("SIGNING_KEY_ALIAS"),System.getenv("SIGNING_KEY_PASSWORD").toCharArray());
    Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update(Files.readAllBytes(Path.of(args[0])));
    Files.writeString(Path.of(args[1]),Base64.getEncoder().encodeToString(signer.sign()));
  }
}
