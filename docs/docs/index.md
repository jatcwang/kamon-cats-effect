---
layout: home
title:  "Home"
section: "home"
position: 1
---

[![Release](https://img.shields.io/nexus/r/com.github.jatcwang/kamon-cats-effect_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/com/github/jatcwang/kamon-cats-effect_2.13/)
[![(https://badges.gitter.im/gitterHQ/gitter.png)](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-cats-effect)

Kamon-cats-effect (and kamon-fs2) helps you integrate your code that uses [cats-effect](https://typelevel.org/cats-effect/) 
and [fs2](https://fs2.io/) with [Kamon](https://github.com/kamon-io/Kamon), 
an open source toolkit for distributed tracing monitoring.

# Installation

```
// SBT
"com.github.jatcwang" %% "kamon-cats-effect" % "{{ site.version }}" 
"com.github.jatcwang" %% "kamon-fs2" % "{{ site.version }}" 

// Mill
ivy"com.github.jatcwang::kamon-cats-effect:{{ site.version }}" 
ivy"com.github.jatcwang::kamon-fs2:{{ site.version }}" 
```
