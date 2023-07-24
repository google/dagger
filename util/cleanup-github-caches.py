"""Cleans out the GitHub Actions cache by deleting obsolete caches.

   Usage:
   python cleanup-github-caches.py
"""

import collections
import datetime
import json
import os
import re
import subprocess
import sys


def main(argv):
  if len(argv) > 1:
    raise ValueError('Expected no arguments: {}'.format(argv))

  # Group caches by their Git reference, e.g "refs/pull/3968/merge"
  caches_by_ref = collections.defaultdict(list)
  for cache in get_caches():
    caches_by_ref[cache['ref']].append(cache)

  # Caclulate caches that should be deleted.
  caches_to_delete = []
  for ref, caches in caches_by_ref.items():
    # If the pull request is already "closed", then delete all caches.
    if (ref != 'refs/heads/master'):
      pull_request_number = re.findall(r'refs/pull/(\d+)/merge', ref)[0]
      pull_request = get_pull_request(pull_request_number)
      if pull_request['state'] == 'closed':
        caches_to_delete += caches
        continue

    # Check for caches with the same key prefix and delete the older caches.
    caches_by_key = {}
    for cache in caches:
      key_prefix = re.findall('(.*)-.*', cache['key'])[0]
      if key_prefix in caches_by_key:
        prev_cache = caches_by_key[key_prefix]
        if (get_created_at(cache) > get_created_at(prev_cache)):
          caches_to_delete.append(prev_cache)
          caches_by_key[key_prefix] = cache
        else:
          caches_to_delete.append(cache)
      else:
        caches_by_key[key_prefix] = cache

  for cache in caches_to_delete:
    print('Deleting cache ({}): {}'.format(cache['ref'], cache['key']))
    print(delete_cache(cache))


def get_created_at(cache):
  return datetime.datetime.strptime(
      cache['created_at'].split('.')[0],
      '%Y-%m-%dT%H:%M:%S'
  )


def delete_cache(cache):
  # pylint: disable=line-too-long
  """Deletes the given cache from GitHub Actions.

  See https://docs.github.com/en/rest/actions/cache?apiVersion=2022-11-28#delete-a-github-actions-cache-for-a-repository-using-a-cache-id

  Args:
    cache: The cache to delete.

  Returns:
    The response of the api call.
  """
  return call_github_api(
      """-X DELETE \
      https://api.github.com/repos/google/dagger/actions/caches/{0}
      """.format(cache['id'])
  )


def get_caches():
  # pylint: disable=line-too-long
  """Gets the list of existing caches from GitHub Actions.

  See https://docs.github.com/en/rest/actions/cache?apiVersion=2022-11-28#list-github-actions-caches-for-a-repository

  Returns:
    The list of existing caches.
  """
  result = call_github_api(
      'https://api.github.com/repos/google/dagger/actions/caches'
  )
  return json.loads(result)['actions_caches']


def get_pull_request(pr_number):
  # pylint: disable=line-too-long
  """Gets the pull request with given number from GitHub Actions.

  See https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#get-a-pull-request

  Args:
    pr_number: The pull request number used to get the pull request.

  Returns:
    The pull request.
  """
  result = call_github_api(
      'https://api.github.com/repos/google/dagger/pulls/{0}'.format(pr_number)
  )
  return json.loads(result)


def call_github_api(endpoint):
  auth_cmd = ''
  if 'GITHUB_TOKEN' in os.environ:
    token = os.environ.get('GITHUB_TOKEN')
    auth_cmd = '-H "Authorization: Bearer {0}"'.format(token)
  cmd = """curl -L \
      {auth_cmd} \
      -H \"Accept: application/vnd.github+json\" \
      -H \"X-GitHub-Api-Version: 2022-11-28\" \
      {endpoint}""".format(auth_cmd=auth_cmd, endpoint=endpoint)
  return subprocess.run(
      [cmd],
      check=True,
      shell=True,
      capture_output=True
  ).stdout.decode('utf-8')


if __name__ == '__main__':
  main(sys.argv)
