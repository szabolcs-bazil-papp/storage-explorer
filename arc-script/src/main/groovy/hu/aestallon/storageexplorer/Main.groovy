package hu.aestallon.storageexplorer

import com.aestallon.storageexplorer.arcscript.api.Arc

static void main(String[] args) {
  def qs = Arc.evaluate("""
    query {
          a 'User'
       from 'org'
      where { str 'name' is false } and { bool 'inactive' is false } and { json 'attributes' overlaps { default true } }
    }""")
  println qs
}
