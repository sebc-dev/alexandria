package dev.alexandria.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the ONNX Runtime environment with optimal threading options before any ONNX model
 * beans are created.
 *
 * <p>Implements {@link BeanFactoryPostProcessor} to guarantee the {@link OrtEnvironment} singleton
 * is initialized with custom threading options BEFORE Spring instantiates the {@code
 * BgeSmallEnV15QuantizedEmbeddingModel} bean. That bean's static initializer calls {@code
 * OrtEnvironment.getEnvironment()}, and the environment is a singleton that cannot be reconfigured
 * after first creation.
 *
 * <p>Threading configuration (optimized for 4-core machine):
 *
 * <ul>
 *   <li>Thread spinning disabled — reduces idle CPU from ONNX worker threads
 *   <li>4 intra-op threads — parallelism within a single inference operation
 *   <li>2 inter-op threads — parallelism across independent inference operations
 * </ul>
 */
@Configuration
@SuppressWarnings("NullAway")
public class OnnxRuntimeConfig implements BeanFactoryPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeConfig.class);

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    try (var threadingOptions = new OrtEnvironment.ThreadingOptions()) {
      threadingOptions.setGlobalSpinControl(false);
      threadingOptions.setGlobalIntraOpNumThreads(4);
      threadingOptions.setGlobalInterOpNumThreads(2);

      OrtEnvironment.getEnvironment(
          OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "alexandria", threadingOptions);

      log.info("ONNX Runtime initialized: spinning=off, intra-op=4, inter-op=2");
    } catch (OrtException e) {
      throw new RuntimeException("Failed to configure ONNX Runtime threading", e);
    } catch (IllegalStateException e) {
      log.warn(
          "ONNX Runtime environment already initialized — threading options not applied. "
              + "This can happen if a library loaded the ONNX environment before Spring context "
              + "startup. Detail: {}",
          e.getMessage());
    }
  }
}
