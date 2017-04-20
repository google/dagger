/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.cycle;

import dagger.Component;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Component with a long enough cycle such that the initialization of a provider happens in a
 * separate {@code initialize} method from the one where it is used as a delegated factory.
 *
 */
// Each nested class's constructor has an intentionally unused parameter.
@SuppressWarnings("unused")
final class LongCycle {
  static class Class1 { @Inject Class1(Class2 class2) {} }
  static class Class2 { @Inject Class2(Class3 class3) {} }
  static class Class3 { @Inject Class3(Class4 class4) {} }
  static class Class4 { @Inject Class4(Class5 class5) {} }
  static class Class5 { @Inject Class5(Class6 class6) {} }
  static class Class6 { @Inject Class6(Class7 class7) {} }
  static class Class7 { @Inject Class7(Class8 class8) {} }
  static class Class8 { @Inject Class8(Class9 class9) {} }
  static class Class9 { @Inject Class9(Class10 class10) {} }
  static class Class10 { @Inject Class10(Class11 class11) {} }
  static class Class11 { @Inject Class11(Class12 class12) {} }
  static class Class12 { @Inject Class12(Class13 class13) {} }
  static class Class13 { @Inject Class13(Class14 class14) {} }
  static class Class14 { @Inject Class14(Class15 class15) {} }
  static class Class15 { @Inject Class15(Class16 class16) {} }
  static class Class16 { @Inject Class16(Class17 class17) {} }
  static class Class17 { @Inject Class17(Class18 class18) {} }
  static class Class18 { @Inject Class18(Class19 class19) {} }
  static class Class19 { @Inject Class19(Class20 class20) {} }
  static class Class20 { @Inject Class20(Class21 class21) {} }
  static class Class21 { @Inject Class21(Class22 class22) {} }
  static class Class22 { @Inject Class22(Class23 class23) {} }
  static class Class23 { @Inject Class23(Class24 class24) {} }
  static class Class24 { @Inject Class24(Class25 class25) {} }
  static class Class25 { @Inject Class25(Class26 class26) {} }
  static class Class26 { @Inject Class26(Class27 class27) {} }
  static class Class27 { @Inject Class27(Class28 class28) {} }
  static class Class28 { @Inject Class28(Class29 class29) {} }
  static class Class29 { @Inject Class29(Class30 class30) {} }
  static class Class30 { @Inject Class30(Class31 class31) {} }
  static class Class31 { @Inject Class31(Class32 class32) {} }
  static class Class32 { @Inject Class32(Class33 class33) {} }
  static class Class33 { @Inject Class33(Class34 class34) {} }
  static class Class34 { @Inject Class34(Class35 class35) {} }
  static class Class35 { @Inject Class35(Class36 class36) {} }
  static class Class36 { @Inject Class36(Class37 class37) {} }
  static class Class37 { @Inject Class37(Class38 class38) {} }
  static class Class38 { @Inject Class38(Class39 class39) {} }
  static class Class39 { @Inject Class39(Class40 class40) {} }
  static class Class40 { @Inject Class40(Class41 class41) {} }
  static class Class41 { @Inject Class41(Class42 class42) {} }
  static class Class42 { @Inject Class42(Class43 class43) {} }
  static class Class43 { @Inject Class43(Class44 class44) {} }
  static class Class44 { @Inject Class44(Class45 class45) {} }
  static class Class45 { @Inject Class45(Class46 class46) {} }
  static class Class46 { @Inject Class46(Class47 class47) {} }
  static class Class47 { @Inject Class47(Class48 class48) {} }
  static class Class48 { @Inject Class48(Class49 class49) {} }
  static class Class49 { @Inject Class49(Class50 class50) {} }
  static class Class50 { @Inject Class50(Class51 class51) {} }
  static class Class51 { @Inject Class51(Class52 class52) {} }
  static class Class52 { @Inject Class52(Class53 class53) {} }
  static class Class53 { @Inject Class53(Class54 class54) {} }
  static class Class54 { @Inject Class54(Class55 class55) {} }
  static class Class55 { @Inject Class55(Class56 class56) {} }
  static class Class56 { @Inject Class56(Class57 class57) {} }
  static class Class57 { @Inject Class57(Class58 class58) {} }
  static class Class58 { @Inject Class58(Class59 class59) {} }
  static class Class59 { @Inject Class59(Class60 class60) {} }
  static class Class60 { @Inject Class60(Class61 class61) {} }
  static class Class61 { @Inject Class61(Class62 class62) {} }
  static class Class62 { @Inject Class62(Class63 class63) {} }
  static class Class63 { @Inject Class63(Class64 class64) {} }
  static class Class64 { @Inject Class64(Class65 class65) {} }
  static class Class65 { @Inject Class65(Class66 class66) {} }
  static class Class66 { @Inject Class66(Class67 class67) {} }
  static class Class67 { @Inject Class67(Class68 class68) {} }
  static class Class68 { @Inject Class68(Class69 class69) {} }
  static class Class69 { @Inject Class69(Class70 class70) {} }
  static class Class70 { @Inject Class70(Class71 class71) {} }
  static class Class71 { @Inject Class71(Class72 class72) {} }
  static class Class72 { @Inject Class72(Class73 class73) {} }
  static class Class73 { @Inject Class73(Class74 class74) {} }
  static class Class74 { @Inject Class74(Class75 class75) {} }
  static class Class75 { @Inject Class75(Class76 class76) {} }
  static class Class76 { @Inject Class76(Class77 class77) {} }
  static class Class77 { @Inject Class77(Class78 class78) {} }
  static class Class78 { @Inject Class78(Class79 class79) {} }
  static class Class79 { @Inject Class79(Class80 class80) {} }
  static class Class80 { @Inject Class80(Class81 class81) {} }
  static class Class81 { @Inject Class81(Class82 class82) {} }
  static class Class82 { @Inject Class82(Class83 class83) {} }
  static class Class83 { @Inject Class83(Class84 class84) {} }
  static class Class84 { @Inject Class84(Class85 class85) {} }
  static class Class85 { @Inject Class85(Class86 class86) {} }
  static class Class86 { @Inject Class86(Class87 class87) {} }
  static class Class87 { @Inject Class87(Class88 class88) {} }
  static class Class88 { @Inject Class88(Class89 class89) {} }
  static class Class89 { @Inject Class89(Class90 class90) {} }
  static class Class90 { @Inject Class90(Class91 class91) {} }
  static class Class91 { @Inject Class91(Class92 class92) {} }
  static class Class92 { @Inject Class92(Class93 class93) {} }
  static class Class93 { @Inject Class93(Class94 class94) {} }
  static class Class94 { @Inject Class94(Class95 class95) {} }
  static class Class95 { @Inject Class95(Class96 class96) {} }
  static class Class96 { @Inject Class96(Class97 class97) {} }
  static class Class97 { @Inject Class97(Class98 class98) {} }
  static class Class98 { @Inject Class98(Class99 class99) {} }
  static class Class99 { @Inject Class99(Class100 class100) {} }
  static class Class100 { @Inject Class100(Class101 class101) {} }
  static class Class101 { @Inject Class101(Provider<Class1> class1Provider) {} }

  @SuppressWarnings("dependency-cycle")
  @Component
  interface LongCycleComponent {
    Class1 class1();
  }

  private LongCycle() {}
}
