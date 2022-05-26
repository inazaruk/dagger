/*
 * Copyright (C) 2021 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.spi.model;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import javax.annotation.processing.ProcessingEnvironment;

/** Wrapper type for an element. */
@AutoValue
public abstract class DaggerProcessingEnv {
  public static DaggerProcessingEnv from(XProcessingEnv processingEnv) {
    return new AutoValue_DaggerProcessingEnv(processingEnv);
  }

  public abstract XProcessingEnv xprocessing();

  public ProcessingEnvironment java() {
    return toJavac(xprocessing());
  }
}
