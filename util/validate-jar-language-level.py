"""Validates classes in the deployed jar have a max java language level .

Usage:
  python validate-jar-language-level.py <jar-file> <max-java-language-level>
"""

import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


_LANGUAGE_LEVEL_PATTERN = re.compile(r'major version: (\d+)')


def main(argv):
  if len(argv) > 3:
    raise ValueError(
        'Expected only two arguments but got {0}'.format(len(argv))
    )

  jar_file, expected_language_level = argv[-2:]
  print(
      'Processing {0} with expected language level {1}...'.format(
          jar_file,
          expected_language_level
      )
  )
  if jar_file.endswith('.jar'):
    invalid_entries = _invalid_language_level(jar_file, expected_language_level)
  elif jar_file.endswith('.aar'):
    dirpath = tempfile.mkdtemp()
    with zipfile.ZipFile(jar_file, 'r') as zip_file:
      class_file = zip_file.extract('classes.jar', dirpath)
      invalid_entries = _invalid_language_level(
          class_file,
          expected_language_level
      )
    shutil.rmtree(dirpath)
  else:
    raise ValueError('Invalid jar file: {0}'.format(jar_file))

  if invalid_entries:
    raise ValueError(
        'Found invalid entries in {0} that do not match the expected java'
        ' language level ({1}):\n    {2}'.format(
            jar_file, expected_language_level, '\n    '.join(invalid_entries)
        )
    )


def _invalid_language_level(jar_file, expected_language_level):
  """Returns a list of jar entries with invalid language levels."""
  invalid_entries = []
  with zipfile.ZipFile(jar_file, 'r') as zip_file:
    class_infolist = [
        info for info in zip_file.infolist()
        if (
            not info.is_dir()
            and info.filename.endswith('.class')
            and not is_shaded_class(info.filename)
        )
    ]
    num_classes = len(class_infolist)
    for i, info in enumerate(class_infolist):
      cmd = 'javap -cp {0} -v {1}'.format(jar_file, info.filename[:-6])
      output1 = subprocess.run(
          cmd.split(),
          stdout=subprocess.PIPE,
          text=True,
          check=True,
      )
      matches = _LANGUAGE_LEVEL_PATTERN.findall(output1.stdout)
      if len(matches) != 1:
        raise ValueError('Expected exactly one match but found: %s' % matches)
      class_language_level = matches[0]
      if class_language_level != expected_language_level:
        invalid_entries.append(
            '{0}: {1}'.format(info.filename, class_language_level)
        )
      # This can take a while so print an update.
      print(
          '  ({0} of {1}) Found language level {2}: {3}'.format(
              i + 1,
              num_classes,
              class_language_level,
              info.filename,
          )
      )

  return invalid_entries


def is_shaded_class(filename):
  # Ignore the shaded deps because we don't really control these classes.
  shaded_prefixes = [
      'dagger/spi/internal/shaded/',
  ]
  for shaded_prefix in shaded_prefixes:
    if filename.startswith(shaded_prefix):
      return True
  return False


if __name__ == '__main__':
  main(sys.argv)
