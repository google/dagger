package dagger.internal.codegen;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import kotlin.Metadata;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

final class KotlinUtil {

  static Optional<KmClass> kmClassOf(Optional<TypeElement> optionalTypeElement) {
    if (!optionalTypeElement.isPresent()) {
      return Optional.empty();
    }
    Metadata metadataAnnotation = optionalTypeElement.get().getAnnotation(Metadata.class);
    KotlinClassHeader header = new KotlinClassHeader(
        metadataAnnotation.k(),
        metadataAnnotation.mv(),
        metadataAnnotation.bv(),
        metadataAnnotation.d1(),
        metadataAnnotation.d2(),
        metadataAnnotation.xs(),
        metadataAnnotation.pn(),
        metadataAnnotation.xi()
    );
    KotlinClassMetadata metadata = KotlinClassMetadata.read(header);
    if (metadata == null) {
      // Should only happen on Kotlin <1.1
      return Optional.empty();
    }
    if (metadata instanceof KotlinClassMetadata.Class) {
      return Optional.of(((KotlinClassMetadata.Class) metadata).toKmClass());
    } else {
      // Unsupported
      return Optional.empty();
    }
  }

  private KotlinUtil() {

  }
}
