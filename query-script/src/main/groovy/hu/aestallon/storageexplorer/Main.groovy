package hu.aestallon.storageexplorer

import com.aestallon.storageexplorer.arcscript.api.ArcScript

static void main(String[] args) {
  def qs = ArcScript.evaluate("""
    query {
          a 'User'
       from 'org'
      where { str 'name' is false } and { bool 'inactive' is false }
    }""")
  println qs
}
