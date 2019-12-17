"""Find value of a Maven pom attribute given a pom file.

   Usage:
   python find_pom_value.py <pom-file> <pom-attribute>
"""
import sys
import xml.etree.ElementTree as Xml


def main(argv):
  pom_file = argv[1]
  pom_attribute = argv[2]
  print(
      Xml.ElementTree(file=pom_file).findtext(
          "{http://maven.apache.org/POM/4.0.0}%s" % pom_attribute))


if __name__ == "__main__":
  main(sys.argv)
