"""statistics.py: Generate usage statistics and such-like."""

from datetime import datetime, timedelta
import os
import webapp2 as webapp

from google.appengine.ext import deferred

import model as mdl
from model import empire as empire_mdl
from model import statistics as stats_mdl
import tasks


def _count_nday_actives(num_days, first_day):
  query = mdl.LoginHistory.all().filter("date >", first_day)
  logins = {}
  for login in query:
    empire_key = mdl.LoginHistory.empire.get_value_for_datastore(login)
    if empire_key in logins:
      logins[empire_key] += 1
    else:
      logins[empire_key] = 1

  today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
  for actives_mdl in stats_mdl.ActiveEmpires.all().filter("date", today):
    if actives_mdl.numDays == num_days:
      actives_mdl.delete()

  actives_mdl = stats_mdl.ActiveEmpires()
  actives_mdl.date = today
  actives_mdl.numDays = num_days
  actives_mdl.actives = len(logins)
  actives_mdl.put()


def count_nday_actives(num_days):
  """Goes through the LoginHistory objects and counts the number of distinct empires."""
  first_day = datetime.now() - timedelta(num_days)
  _count_nday_actives(num_days, first_day)


def update_empire_rank(empire_key):
  """Goes through the planets, stars & fleets for the given empire and updates their rank."""
  totalShips = 0
  for fleet_mdl in empire_mdl.Fleet.all(projection=("numShips",)).filter("empire", empire_key):
    totalShips += fleet_mdl.numShips
  stars = []
  totalColonies = 0
  for colony_mdl in empire_mdl.Colony.all(projection=("planet_index",)).filter("empire", empire_key):
    star_key = str(colony_mdl.key().parent())
    if star_key not in stars:
      stars.append(star_key)
    totalColonies += 1
  totalStars = len(stars)
  totalBuildings = empire_mdl.Building.all().filter("empire", empire_key).count()

  empire_rank_mdl = None
  for mdl in stats_mdl.EmpireRank.all().filter("empire", empire_key).fetch(1):
    empire_rank_mdl = mdl
    break
  if not empire_rank_mdl:
    empire_rank_mdl = stats_mdl.EmpireRank()
    empire_rank_mdl.empire = empire_key
  empire_rank_mdl.totalStars = totalStars
  empire_rank_mdl.totalColonies = totalColonies
  empire_rank_mdl.totalBuildings = totalBuildings
  empire_rank_mdl.totalShips = int(totalShips)
  empire_rank_mdl.put()


class GeneratePage(tasks.TaskPage):
  def get(self):
    deferred.defer(count_nday_actives, 1,
                   _queue="statistics")
    deferred.defer(count_nday_actives, 7,
                   _queue="statistics")
    for empire_key in empire_mdl.Empire.all(keys_only=True):
      deferred.defer(update_empire_rank, empire_key,
                     _queue="statistics")

app = webapp.WSGIApplication([("/tasks/stats/generate", GeneratePage)],
                             debug=os.environ["SERVER_SOFTWARE"].startswith("Development"))
