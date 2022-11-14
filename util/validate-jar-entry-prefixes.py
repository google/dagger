"""Validates classes in the deployed jar are all within the expected packages.

   Usage:
   python validate-jar-entry-prefixes.py <jar-file> <comma-separated-prefixes>
"""
import re
import shutil
import sys
import tempfile
import zipfile


def main(argv):
  if len(argv) > 3:
    raise Exception('Expected only two arguments but got {0}'.format(len(argv)))

  jar_file, prefixes = argv[-2:]
  prefixes_pattern = re.compile('|'.join(prefixes.split(',')))

  invalid_entries = []
  if jar_file.endswith('.jar'):
    invalid_entries = _invalid_entries(jar_file, prefixes_pattern)
  elif jar_file.endswith('.aar'):
    dirpath = tempfile.mkdtemp()
    with zipfile.ZipFile(jar_file, 'r') as zip_file:
      class_file = zip_file.extract('classes.jar', dirpath)
      invalid_entries = _invalid_entries(class_file, prefixes_pattern)
    shutil.rmtree(dirpath)
  else:
    raise Exception('Invalid jar file: {0}'.format(jar_file))

  if invalid_entries:
    raise Exception(
        'Found invalid entries in {0} that do not match one of the allowed prefixes ({1}):\n    {2}'
        .format(
            jar_file,
            ', '.join(['"{0}"'.format(p) for p in prefixes.split(',')]),
            '\n    '.join(invalid_entries))
        )


def _invalid_entries(jar_file, prefixes_pattern):
  invalid_entries = []
  with zipfile.ZipFile(jar_file, 'r') as zip_file:
    for info in zip_file.infolist():
      if not info.is_dir():
        if not prefixes_pattern.match(info.filename):
          invalid_entries.append(info.filename)
  return invalid_entries


if __name__ == '__main__':
  main(sys.argv)
