package hu.aestallon.storageexplorer

import com.aestallon.storageexplorer.arcscript.api.Arc

static void main(String[] args) {
  def arcScript = Arc.parse(/*"""
    query {
          a 'User'
       from 'org'
      where { 
        str 'name' is 'Attila' 
      } and (expr { 
        bool 'inactive' is false 
      } and { 
        json 'attributes' overlaps { 'default' true } 
      })
    }"""*/
  """
    query {
          a 'User'
       from 'org'
      where (exp
        { str 'name' is 'Foo' } and (expr { num 'age' is 9 } or { num 'age' is 8 } )
      ) or (expr
        {str 'name' contains 'Baz' } or { json 'attributes' overlaps { builtIn true } }
      )
    }""")
  def qs = Arc.evaluate arcScript
  println qs
}
