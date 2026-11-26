package tn.rnu.eniso.doserunner.dispatch.bo;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackagedDispatchStrategyIT {
    @Test
    void discoversAndExecutesProviderFromExecutableJar() throws Exception {
        Path jar = Path.of(System.getProperty("packaged.application")).toAbsolutePath();
        assertThat(jar).isRegularFile();
        URL classes = URI.create("jar:" + jar.toUri() + "!/BOOT-INF/classes/").toURL();
        // The platform parent cannot supply application classes or service descriptors from the test classpath.
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes}, ClassLoader.getPlatformClassLoader())) {
            Class<?> spi = loader.loadClass("tn.rnu.eniso.doserunner.dispatch.bo.DispatchStrategy");
            List<?> providers = ServiceLoader.load(spi, loader).stream().map(ServiceLoader.Provider::get).toList();
            assertThat(spi.getClassLoader()).isSameAs(loader);
            assertThat(providers).hasSize(1);
            Object provider = providers.getFirst();
            assertThat(provider.getClass().getName())
                    .isEqualTo("tn.rnu.eniso.doserunner.dispatch.bo.NearestIdleRobotStrategy");
            assertThat(provider.getClass().getClassLoader()).isSameAs(loader);
            assertThat(spi.getMethod("select", Collection.class, int.class, int.class)
                    .invoke(provider, List.of(), 0, 0)).isEqualTo(Optional.empty());
        }
    }
}
