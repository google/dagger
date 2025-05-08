allprojects {
  // Configure build output directory on all projects to use 'buildOut' instead of the default
  // 'build' to avoid conflicts with BUILD files in case-insensitive file systems (i.e. Mac OS).
  layout.buildDirectory.set(layout.projectDirectory.dir("buildOut"))
}
